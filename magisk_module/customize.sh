SKIPUNZIP=0
ui_print "- Installing VEIL System Service..."
mkdir -p $MODPATH/system/bin
mkdir -p $MODPATH/system/lib/modules
cp -f $ZIPFILE veil.ko $MODPATH/system/lib/modules/veil.ko
chmod 644 $MODPATH/system/lib/modules/veil.ko
cp -f $ZIPFILE veil_daemon $MODPATH/system/bin/veil_daemon
chmod 755 $MODPATH/system/bin/veil_daemon
ui_print "- Setting permissions..."
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/system/bin/veil_daemon 0 0 0755
set_perm $MODPATH/system/lib/modules/veil.ko 0 0 0644
ui_print "- Installation complete!"
