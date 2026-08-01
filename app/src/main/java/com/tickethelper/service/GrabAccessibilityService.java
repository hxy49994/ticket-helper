package com.tickethelper.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.tickethelper.engine.ClickStrategy;
import com.tickethelper.engine.GrabConfig;
import com.tickethelper.engine.GrabState;

import java.util.ArrayList;
import java.util.List;

public class GrabAccessibilityService extends AccessibilityService {
    private static final String TAG = "GrabService";
    private static final String TARGET_PACKAGE = "com.damai";
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";

    private static GrabAccessibilityService instance;
    private boolean isGrabbing = false;
    private int currentStep = 0;
    private int retryCount = 0;
    private GrabConfig config;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GrabState.StateCallback stateCallback;

    public static GrabAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "无障碍服务已创建");
    }

    @Override
    public void onDestroy() {
        instance = null;
        stopGrabbing();
        Log.i(TAG, "无障碍服务已销毁");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isGrabbing || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        if (pkg.equals(ALIPAY_PACKAGE)) {
            onAlipayDetected();
            return;
        }
        if (!pkg.equals(TARGET_PACKAGE)) return;

        Log.d(TAG, "大麦事件: type=" + event.getEventType() + " className=" + event.getClassName());
        executeCurrentStep();
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "无障碍服务被中断");
    }

    public void startGrabbing(GrabConfig config) {
        this.config = config;
        this.isGrabbing = true;
        this.currentStep = 0;
        this.retryCount = 0;
        Log.i(TAG, "开始抢票流程, 配置=" + config);
        updateState(GrabState.STEP_WAIT, "抢票流程已启动");
        handler.postDelayed(this::executeCurrentStep, config.clickInterval);
    }

    public void stopGrabbing() {
        this.isGrabbing = false;
        this.retryCount = 0;
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "抢票流程已停止");
        updateState(GrabState.STEP_IDLE, "已停止");
    }

    public boolean isGrabbing() {
        return isGrabbing;
    }

    public void setStateCallback(GrabState.StateCallback callback) {
        this.stateCallback = callback;
    }

    private void executeCurrentStep() {
        if (!isGrabbing) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "获取根节点失败，重试中...");
            scheduleRetry();
            return;
        }

        switch (currentStep) {
            case GrabState.STEP_BUY:
                tryClickText(root, "立即购买", () -> advanceStep(GrabState.STEP_SUBMIT, "点击立即购买"));
                tryClickText(root, "选座购买", () -> advanceStep(GrabState.STEP_SUBMIT, "点击选座购买"));
                tryClickText(root, "马上预订", () -> advanceStep(GrabState.STEP_SUBMIT, "点击马上预订"));
                tryClickSessionItem(root);
                break;
            case GrabState.STEP_SUBMIT:
                tryClickText(root, "提交订单", () -> advanceStep(GrabState.STEP_PAY, "点击提交订单"));
                tryClickText(root, "立即支付", () -> advanceStep(GrabState.STEP_PAY, "点击立即支付"));
                tryClickText(root, "去支付", () -> advanceStep(GrabState.STEP_PAY, "点击去支付"));
                break;
            case GrabState.STEP_PAY:
                tryClickText(root, "支付宝", () -> advanceStep(GrabState.STEP_DONE, "选择支付宝支付"));
                tryClickText(root, "确认支付", () -> advanceStep(GrabState.STEP_DONE, "点击确认支付"));
                break;
            default:
                break;
        }
        root.recycle();
        scheduleRetry();
    }

    private void tryClickText(AccessibilityNodeInfo root, String text, Runnable onSuccess) {
        List<AccessibilityNodeInfo> nodes = findNodesByText(root, text);
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null && node.isVisibleToUser() && node.isEnabled()) {
                ClickStrategy.clickNode(node);
                Log.i(TAG, "找到目标: " + text + "，执行点击");
                onSuccess.run();
                return;
            }
        }
    }

    private void tryClickSessionItem(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> buttons = findNodesByClassName(root, "android.widget.Button");
        List<AccessibilityNodeInfo> texts = findNodesByClassName(root, "android.widget.TextView");
        int targetIndex = config != null ? config.sessionIndex : 0;

        if (!buttons.isEmpty() && targetIndex < buttons.size()) {
            ClickStrategy.clickNode(buttons.get(targetIndex));
            Log.i(TAG, "点击第" + (targetIndex + 1) + "个按钮");
            advanceStep(GrabState.STEP_SUBMIT, "选择场次");
        } else if (!texts.isEmpty() && targetIndex < texts.size()) {
            ClickStrategy.clickNode(texts.get(targetIndex));
            Log.i(TAG, "点击第" + (targetIndex + 1) + "个文本");
            advanceStep(GrabState.STEP_SUBMIT, "选择场次");
        }
    }

    private List<AccessibilityNodeInfo> findNodesByText(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> results = new ArrayList<>();
        if (node == null) return results;

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;

            CharSequence nodeText = child.getText();
            CharSequence contentDesc = child.getContentDescription();
            String viewId = child.getViewIdResourceName();

            if ((nodeText != null && nodeText.toString().contains(text))
                    || (contentDesc != null && contentDesc.toString().contains(text))
                    || (viewId != null && viewId.contains(text))) {
                results.add(child);
            }
            results.addAll(findNodesByText(child, text));
        }
        return results;
    }

    private List<AccessibilityNodeInfo> findNodesByClassName(AccessibilityNodeInfo node, String className) {
        List<AccessibilityNodeInfo> results = new ArrayList<>();
        if (node == null) return results;

        if (className.equals(node.getClassName().toString())) {
            results.add(node);
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                results.addAll(findNodesByClassName(child, className));
            }
        }
        return results;
    }

    private void advanceStep(int nextStep, String action) {
        retryCount = 0;
        currentStep = nextStep;
        updateState(currentStep, action + " - 成功");
        Log.i(TAG, "进入步骤: " + currentStep);
        handler.postDelayed(this::executeCurrentStep, config != null ? config.clickInterval : 500);
    }

    private void scheduleRetry() {
        if (!isGrabbing) return;
        retryCount++;
        int maxRetry = config != null ? config.maxRetry : 30;
        if (retryCount > maxRetry) {
            Log.w(TAG, "重试超限，停止流程");
            updateState(GrabState.STEP_IDLE, "重试超限，未找到目标");
            isGrabbing = false;
            return;
        }
        handler.postDelayed(this::executeCurrentStep, config != null ? config.clickInterval : 500);
    }

    private void onAlipayDetected() {
        Log.i(TAG, "检测到支付宝APP启动，抢票成功！");
        isGrabbing = false;
        updateState(GrabState.STEP_DONE, "已跳转支付宝，抢票完成！");
    }

    private void updateState(int step, String message) {
        if (stateCallback != null) {
            stateCallback.onStateChanged(step, message);
        }
    }
}
