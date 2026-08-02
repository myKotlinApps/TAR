package com.syshelper.service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import java.io.File;

public class SilentInstaller {
    public static void install(Context ctx, String apkPath) {
        try {
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.getPackageManager().canRequestPackageInstalls()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, ctx.getPackageName());
            ctx.startActivity(intent);
        } catch (Exception ignored) {}
    }
}
