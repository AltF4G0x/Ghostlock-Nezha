#!/system/bin/sh
# ghostlock: Write 1 smears selinux_state (enforcing + policycap[]) with
# pointer bytes. Reloading the live policy in a single write() rebuilds the
# policydb, resets policycap[], and flushes the AVC cache. Harmless when
# state is already clean.
ENF=$(cat /sys/fs/selinux/enforce 2>/dev/null)
echo 0 > /sys/fs/selinux/enforce 2>/dev/null
sleep 2
dd if=/sys/fs/selinux/policy of=/sys/fs/selinux/load bs=8388608 2>/dev/null
[ "$ENF" = "1" ] && echo 1 > /sys/fs/selinux/enforce 2>/dev/null
mkdir -p /data/adb/ksu/log 2>/dev/null
echo "$(date '+%F %T') ghostlock policycap fix, enforce=$(cat /sys/fs/selinux/enforce 2>/dev/null)" >> /data/adb/ksu/log/ghostlock.log 2>/dev/null
