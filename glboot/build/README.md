# GLBoot build notes

GLBoot is built manually with the Android SDK command-line tools (no Gradle,
no `res/` directory — the manifest only references platform resources).
The signed output `glboot.apk` and `glboot.keystore` are stored encrypted in
`../../binaries.7z` (password `glboot`), not in git.

Toolchain used:

- `aapt2`, `d8`, `zipalign`, `apksigner` from `android-sdk/build-tools/<ver>/`
- `javac` from JDK 21
- `android.jar` from `android-sdk/platforms/android-35/`

Build steps (Git Bash, from the `glboot/` directory):

```sh
BT=/c/Users/oasys/android-sdk/build-tools/<ver>
AJAR=/c/Users/oasys/android-sdk/platforms/android-35/android.jar

# 1. Package manifest + assets into an unsigned APK
"$BT/aapt2" link -o build/glboot-zip.apk -I "$AJAR" \
    --manifest AndroidManifest.xml -A assets

# 2. Compile Java sources (no R.java — no app resources)
javac -source 11 -target 11 -bootclasspath "$AJAR" \
    -d build/classes $(find src -name '*.java')

# 3. Dex
"$BT/d8" --lib "$AJAR" --output build/dex \
    $(find build/classes -name '*.class')

# 4. Add classes.dex to the APK zip
cd build/dex && zip -j ../glboot-zip.apk classes.dex && cd ../..

# 5. Align + sign
"$BT/zipalign" -f 4 build/glboot-zip.apk build/glboot-aligned.apk
"$BT/apksigner" sign --ks build/glboot.keystore \
    --ks-pass pass:glboot123 --out build/glboot.apk build/glboot-aligned.apk
```

Keystore: `glboot.keystore`, store/key password `glboot123`
(created with `keytool -genkeypair -v -keystore glboot.keystore -alias glboot
-keyalg RSA -keysize 2048 -validity 10000`).

Install: `adb install build/glboot.apk`, then launch GLBoot once so Android
whitelists its BOOT_COMPLETED receiver, and reboot.
