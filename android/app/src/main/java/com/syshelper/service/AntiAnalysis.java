package com.syshelper.service;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Enumeration;

public class AntiAnalysis {
    public static boolean isEmulator() {
        String model = Build.MODEL, product = Build.PRODUCT, hardware = Build.HARDWARE, fingerprint = Build.FINGERPRINT, manufacturer = Build.MANUFACTURER, brand = Build.BRAND;
        String[] flags = {"generic", "generic_x86", "generic_x64", "generic_arm64", "google_sdk", "sdk_google", "Android SDK", "Emulator", "genymotion", "vbox86p", "vbox", "ttVM", "goldfish", "ranchu"};
        for (String f : flags) { if (model.contains(f) || product.contains(f) || hardware.contains(f) || fingerprint.contains(f) || manufacturer.contains(f) || brand.contains(f)) return true; }
        String[] vmFiles = {"/dev/qemu_pipe", "/dev/qemu_trace", "/dev/socket/qemud", "/dev/goldfish_pipe", "/system/lib/libc_malloc_debug_qemu.so", "/sys/qemu_trace", "/system/bin/qemu-props", "/proc/tty/drivers"};
        for (String path : vmFiles) { if (new File(path).exists()) return true; }
        try {
            Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                String name = ni.getName().toLowerCase();
                if (name.contains("eth0")) {
                    String ip = ni.getInetAddresses().nextElement().getHostAddress();
                    if (ip != null && ip.startsWith("10.0.2.")) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isBeingDebugged(Context ctx) {
        if (android.os.Debug.isDebuggerConnected()) return true;
        String[] debugApps = {"com.frida.server", "re.frida.server", "com.frida.agent", "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser", "com.noshufou.android.su"};
        for (String pkg : debugApps) { try { ctx.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception ignored) {} }
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    String pid = line.substring(line.indexOf(":") + 1).trim();
                    if (!pid.equals("0")) return true;
                    break;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isFridaPresent() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ps -A 2>/dev/null || ps"});
            BufferedReader br = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("frida") || lower.contains("gum-js-loop") || lower.contains("linjector") || lower.contains("frida-helper")) return true;
            }
            br.close();
        } catch (Exception ignored) {}
        int[] fridaPorts = {27042, 27043, 27004, 27005};
        for (int port : fridaPorts) {
            try { Socket sock = new Socket(); sock.connect(new InetSocketAddress("127.0.0.1", port), 200); sock.close(); return true; } catch (Exception ignored) {}
        }
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/self/maps"));
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("frida") || lower.contains("gadget") || lower.contains("linjector") || lower.contains("frida-agent")) return true;
            }
            br.close();
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isSandbox(Context ctx) {
        long uptime = SystemClock.elapsedRealtime();
        if (uptime < 10000) return true;
        android.hardware.SensorManager sm = (android.hardware.SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) == null) return true;
        android.content.Intent battery = ctx.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            if (level == 100 && battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING && battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) == 0) return true;
        }
        String[] avApps = {"com.avast.android.mobilesecurity", "com.kaspersky.security", "com.bitdefender.security", "com.mcafee.android.security", "com.symantec.mobilesecurity", "com.lookout"};
        for (String pkg : avApps) { try { ctx.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception ignored) {} }
        return false;
    }

    public static boolean isSafe(Context ctx) {
        if (isEmulator()) return false;
        if (isBeingDebugged(ctx)) return false;
        if (isFridaPresent()) return false;
        if (isSandbox(ctx)) return false;
        return true;
    }
}
