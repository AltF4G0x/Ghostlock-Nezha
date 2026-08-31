#!/system/bin/sh
# GhostLock policycap repair: set config bits 30+31 (cgroup_seclabel,
# always_check_network) in the live SELinux policy and reload.
# Run with SELinux permissive (echo 0 > /sys/fs/selinux/enforce first).
P=/sys/fs/selinux/policy
L=/sys/fs/selinux/load
T=/data/local/tmp/.ghostlock_policy.bin
cp $P $T || { echo "FAIL: cannot read policy"; exit 1; }
SZ=$(wc -c < $T)
echo "policy size: $SZ"
[ "$SZ" -gt 20 ] || { echo "FAIL: policy too small"; exit 1; }
ID_LEN=$(dd if=$T bs=1 skip=4 count=4 2>/dev/null | od -A n -t u4 | tr -d ' ')
CO=$((4 + 4 + ID_LEN + 4))
B=$(dd if=$T bs=1 skip=$CO count=4 2>/dev/null | od -A n -t u1 | tr -s ' ')
set -- $B
echo "config word at +$CO: $4 $3 $2 $1 (hex, LE)"
b0=$1; b1=$2; b2=$3; b3=$(( $4 | 192 ))
EXP=$(printf '%02x%02x%02x%02x' $b3 $b2 $b1 $b0)
o=$(printf '\\%03o\\%03o\\%03o\\%03o' $b0 $b1 $b2 $b3)
printf "$o" | dd of=$T bs=1 seek=$CO conv=notrunc 2>/dev/null
RB=$(dd if=$T bs=1 skip=$CO count=4 2>/dev/null | od -A n -t x1 | tr -d ' \n')
REXP="${EXP:6:2}${EXP:4:2}${EXP:2:2}${EXP:0:2}"
echo "readback bytes: $RB"
[ "$RB" = "$REXP" ] || { echo "FAIL: patch did not stick (want $REXP)"; exit 1; }
echo "patch verified ($EXP)"
dd if=$T of=$L bs=8388608 2>/dev/null && echo "policy reloaded OK" || echo "FAIL: reload"
rm -f $T
