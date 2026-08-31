# ghostlock-nezha — Rooting the Xiaomi 17 Ultra (nezha) with CVE-2026-43499

Consolidated documentation and source for a completed rooting project.
The device is the author's own phone; the exploit is public.

## Layout

- `ghostlock-src/` — modified clone of
  [JoinChang/ghostlock-oneplus](https://github.com/JoinChang/ghostlock-oneplus)
  (futex PI UAF exploit, CVE-2026-43499) with a new `nezha` device entry,
  a fixed root script, and tooling tweaks.
- `glboot/` — GLBoot, a tiny bootstrapper Android app that re-runs the
  exploit automatically at boot (soft root is not persistent).
- `binaries.7z` — encrypted archive (7-Zip, password `glboot`,
  header encryption on) holding all binaries. They are not committed
  in plaintext because Windows Defender deletes the exploit binary and
  APK on sight. Contents (paths as extracted):

  ```
  ghostlock/ghostlock            built exploit binary (arm64)
  ghostlock/kernel               39 MB kernel image extracted from boot.img
  ghostlock/kallsyms.txt         125,508 kernel symbols
  glboot/build/glboot.apk        signed, installable bootstrapper
  glboot/build/glboot.keystore   signing key (password glboot123)
  glboot/assets/e                copy of the exploit binary bundled in the APK
  ```

  Extract: `7z x -pglboot binaries.7z`

## Device / firmware

- Xiaomi 17 Ultra, codename `nezha`, SM8850 (Snapdragon 8 Elite Gen 5)
- HyperOS Global **OS3.0.303.0.WPAMIXM** (2026-05-13)
- Kernel `6.12.23-android16-5-g5a0e85dd9db0-ab14499855-4k` (4K pages)
- boot.img sha1 prefix `f7b6891471`

## Offset extraction

1. Pulled `boot.img` from the OS3.0.303.0.WPAMIXM fastboot/OTA package and
   extracted the raw kernel image (`ghostlock/kernel`).
2. Recovered the symbol table with **vmlinux-to-elf**:
   125,508 symbols → `kallsyms.txt`, `_text = 0xffffffc080000000`.
3. `tools/extract_target.py` (patched to accept a local `kallsyms.txt`
   instead of requiring a rooted device) computes the target offsets and
   verifies them — result: **PASSED, 0 errors**.
4. `tools/extract_btf.py` checks task_struct field offsets against BTF:
   57/60 matched, all fields equal the `STRUCT_OFFSETS_6_12` defaults.
5. `tools/check_feasibility.py` (also reads the local kallsyms) confirms the
   required gadgets/symbols exist.

## The nezha device entry

`ghostlock-src/src/devices/nezha/offsets.h`, included from
`src/devices/offsets.h`. Key values:

- `kernel_phys_load = 0xc7800000` — taken from verified SM8850 entries
  (op15/pudding, same SoC).
- `PSELECT_SHIFT = 0` — stack frames for `futex_wait_requeue_pi` (0x1c0)
  and `core_sys_select` (0x1b0) are identical to the pudding 6.12.23 build
  (SP diff = -64), so no waiter-shift adjustment is needed.
- All symbol offsets from the extracted kallsyms (init_task, init_cred,
  selinux_enforcing, selinux_blob_sizes, ashmem/configfs/splice gadgets,
  and the slide targets `nfulnl_logger`, `loggers_0_1`, `boot_id`).

`offsets.json` (repo: `ghostlock-src/offsets.json`) carries the same values
in the schema expected by the
[YuKongA/ghostlock-app](https://github.com/YuKongA/ghostlock-app) Android
app — import it there to run the exploit from that UI instead.

## The SELinux policycap corruption fix (networking)

The exploit's stage-1 write smears `selinux_state` — both `enforcing` and
the `policycap[]` array — with pointer bytes. After re-enabling SELinux the
corrupted policycaps (notably `cgroup_seclabel` and
`always_check_network`, config bits 30/31) break socket labeling and the
network stops working.

Fix: reloading the live policy **in a single `write()`** rebuilds the
policydb, resets `policycap[]`, and flushes the AVC cache:

```sh
dd if=/sys/fs/selinux/policy of=/sys/fs/selinux/load bs=8388608
```

The 8 MiB single-write matters: a fragmented (multi-write) load is
rejected. This was added to the root script in
`ghostlock-src/src/core/main.c` (`write_root_script()`): after KernelSU is
up, `sleep 5`, reload the policy, then `echo 1 >
/sys/fs/selinux/enforce`. The script also logs to
`/data/local/tmp/a/root.log` for post-mortems.

Related scripts in `ghostlock-src/`:

- `fixpol.sh` — standalone variant that additionally patches the two
  policycap bits directly in the policy blob before reloading (with
  readback verification).
- `svc-fixpol.sh` — KernelSU `service.d` hook (`/data/adb/ksu/service.d/`)
  that applies the single-write reload on every boot, harmless when state
  is already clean.

## GLBoot bootstrapper (`glboot/`)

Soft root dies at reboot, so GLBoot re-runs the exploit automatically:

- `BootReceiver` on `BOOT_COMPLETED` → `Runner.run()`.
- `Runner` copies the bundled exploit (`assets/e`) to the app's filesDir,
  chmods it, and launches `e --bootstrap` detached, logging to
  `filesDir/boot.log`.
- `MainActivity` shows the log (2 s refresh) and a "Run exploit now"
  button for manual attempts.
- No Gradle: built by hand with aapt2 / javac / d8 / zipalign / apksigner —
  exact commands in `glboot/build/README.md`. Keystore password `glboot123`.

Install once (`adb install glboot.apk`), launch it once so its boot
receiver is whitelisted, then every boot re-roots the phone.

## Caveats

- The exploit race must run **within ~30 seconds of boot**; later attempts
  almost never win.
- Expect **occasional failed attempts and kernel panics** — a panic just
  reboots the phone, retry.
- This is **soft root**: nothing is patched on disk; root is re-applied by
  GLBoot at every boot.
- `assets/e`, the APK, the kernel image and the kallsyms dump are excluded
  from git (`.gitignore`) and exist only inside `binaries.7z`.
