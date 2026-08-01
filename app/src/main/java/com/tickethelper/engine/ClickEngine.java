package com.tickethelper.engine;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class ClickEngine {
    private static final String TAG = "ClickEngine";
    private final AccessibilityService service;

    public ClickEngine(AccessibilityService service) {
        this.service = service;
    }

    // 按文字找节点并点击（优先匹配可点击的）
    public boolean findAndClick(String[] keywords) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;

        boolean found = false;
        for (String keyword : keywords) {
            found = findInTree(root, keyword);
            if (found) {
                Log.i(TAG, "点击成功: " + keyword);
                break;
            }
        }
        root.recycle();
        return found;
    }

    private boolean findInTree(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;

        // 匹配文字
        String nodeText = getNodeText(node);
        if (nodeText != null && nodeText.contains(text)) {
            if (clickNode(node)) return true;
        }

        // 递归子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findInTree(child, text)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }

    private String getNodeText(AccessibilityNodeInfo node) {
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) return t.toString();
        CharSequence d = node.getContentDescription();
        if (d != null && d.length() > 0) return d.toString();
        return null;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        // 策略1: 直接点击
        if (node.isClickable()) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }
        // 策略2: 找可点击的父节点
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                boolean ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                if (ok) return true;
                break;
            }
            AccessibilityNodeInfo grand = parent.getParent();
            parent.recycle();
            parent = grand;
        }
        // 策略3: 手势模拟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return gestureClick(node);
        }
        return false;
    }

    private boolean gestureClick(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) return false;

        float x = bounds.centerX();
        float y = bounds.centerY();

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription gesture = builder
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 30))
                .build();
        return service.dispatchGesture(gesture, null, null);
    }
}
