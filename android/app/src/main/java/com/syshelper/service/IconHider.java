package com.syshelper.service;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;

public class IconHider {
    public static void hideIcon(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        ComponentName launcherComponent = new ComponentName(ctx, "com.syshelper.service.MainActivity-Launcher");
        try { pm.setComponentEnabledSetting(launcherComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); } catch (Exception ignored) {}
    }

    public static void applyManufacturerHiding(Context ctx) {
        hideIcon(ctx);
    }
}
