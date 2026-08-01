package com.tickethelper.engine;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.tickethelper.service.GrabAccessibilityService;

public class ClickStrategy {
    private static final String TAG = "ClickStrategy";

    public static boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // 方法1: 直接performAction点击（最快）
        if (node.isClickable()) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "ACTION_CLICK 成功");
                return true;
            }
        }

        // 方法2: 如果自身不可点击，找父节点点击
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isClickable()) {
            if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "父节点 ACTION_CLICK 成功");
                parent.recycle();
                return true;
            }
            parent.recycle();
        }

        // 方法3: 手势模拟点击（兜底方案）
        return clickByGesture(node);
    }

    private static boolean clickByGesture(AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) return false;

        float centerX = bounds.centerX();
        float centerY = bounds.centerY();

        Path path = new Path();
        path.moveTo(centerX, centerY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription gesture = builder
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
                .build();

        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service != null) {
            return service.dispatchGesture(gesture, null, null);
        }
        return false;
    }
}
