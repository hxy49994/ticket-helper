package com.tickethelper.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.tickethelper.engine.ClickEngine;
import com.tickethelper.engine.GrabConfig;
import com.tickethelper.engine.GrabState;
import com.tickethelper.engine.ScheduledTask;

public class GrabAccessibilityService extends AccessibilityService {
    private static final String TAG = "GrabService";
    private static GrabAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ClickEngine clickEngine;
    private ScheduledTask scheduledTask;

    private boolean isGrabbing = false;
    private int currentStep = GrabState.IDLE;
    private int retryCount = 0;
    private GrabConfig config = new GrabConfig();
    private GrabState.Callback stateCallback;

    public static GrabAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        clickEngine = new ClickEngine(this);
        scheduledTask = new ScheduledTask();
        Log.i(TAG, "无障碍服务已启动");
    }

    @Override
    public void onDestroy() {
        stopGrabbing();
        instance = null;
        Log.i(TAG, "无障碍服务已销毁");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isGrabbing) return;
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // 支付宝检测
        if (pkg.contains("alipay") || pkg.contains("Alipay")) {
            Log.i(TAG, "检测到支付宝!");
            onPaymentDetected();
            return;
        }

        // 大麦页面变化 => 执行当前步骤
        if (pkg.equals(config.damaiPackage)) {
            executeCurrentStep();
        }
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "服务中断");
    }

    // ===== 公开API =====

    public void setConfig(GrabConfig cfg) {
        this.config = cfg;
    }

    public void setCallback(GrabState.Callback cb) {
        this.stateCallback = cb;
    }

    public boolean isGrabbing() {
        return isGrabbing;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    // ===== 控制 =====

    public void startScheduledGrab(long startTimeMillis) {
        config.startTimeMillis = startTimeMillis;
        isGrabbing = true;
        currentStep = GrabState.WAITING;
        notifyState("等待开售");

        scheduledTask.startCountdown(startTimeMillis, 1000, new ScheduledTask.TaskCallback() {
            @Override
            public void onTick(long remaining) {
                String msg = "距离开售: " + (remaining / 1000) + "秒";
                notifyState(msg);
            }

            @Override
            public void onTimeUp() {
                Log.i(TAG, "开售时间到！开始抢票");
                notifyState("开售时间到！");
                currentStep = GrabState.CLICK_BUY;
                retryCount = 0;
                handler.postDelayed(() -> executeCurrentStep(), 100);
            }
        });
    }

    public void startGrabbing() {
        isGrabbing = true;
        currentStep = GrabState.CLICK_BUY;
        retryCount = 0;
        notifyState("开始抢票");
        handler.postDelayed(this::executeCurrentStep, 200);
    }

    public void stopGrabbing() {
        isGrabbing = false;
        retryCount = 0;
        scheduledTask.stop();
        handler.removeCallbacksAndMessages(null);
        currentStep = GrabState.IDLE;
        notifyState("已停止");
        Log.i(TAG, "抢票停止");
    }

    // ===== 核心执行 =====

    private void executeCurrentStep() {
        if (!isGrabbing) return;

        switch (currentStep) {
            case GrabState.CLICK_BUY:
                tryClickBuy();
                break;
            case GrabState.CLICK_SUBMIT:
                tryClickSubmit();
                break;
            case GrabState.CLICK_PAY:
                tryClickPay();
                break;
        }
    }

    private void tryClickBuy() {
        if (clickEngine.findAndClick(GrabConfig.BUY_BUTTONS)) {
            retryCount = 0;
            currentStep = GrabState.CLICK_SUBMIT;
            notifyState("已点击购买按钮");
            handler.postDelayed(this::executeCurrentStep, config.clickInterval);
        } else {
            retryCount++;
            if (retryCount > config.maxRetry) {
                notifyState("购买按钮未找到，停止");
                isGrabbing = false;
                return;
            }
            handler.postDelayed(this::executeCurrentStep, config.clickInterval);
        }
    }

    private void tryClickSubmit() {
        if (clickEngine.findAndClick(GrabConfig.SUBMIT_BUTTONS)) {
            retryCount = 0;
            currentStep = GrabState.CLICK_PAY;
            notifyState("已提交订单");
            handler.postDelayed(this::executeCurrentStep, config.clickInterval);
        } else {
            retryCount++;
            if (retryCount > config.maxRetry) {
                notifyState("提交按钮未找到，停止");
                isGrabbing = false;
                return;
            }
            handler.postDelayed(this::executeCurrentStep, config.clickInterval);
        }
    }

    private void tryClickPay() {
        if (clickEngine.findAndClick(GrabConfig.PAY_BUTTONS)) {
            retryCount = 0;
            currentStep = GrabState.DONE;
            notifyState("已跳转支付");
            isGrabbing = false;
        } else {
            retryCount++;
            if (retryCount > config.maxRetry) {
                notifyState("支付按钮未找到，停止");
                isGrabbing = false;
                return;
            }
            handler.postDelayed(this::executeCurrentStep, config.clickInterval);
        }
    }

    private void onPaymentDetected() {
        currentStep = GrabState.DONE;
        isGrabbing = false;
        scheduledTask.stop();
        notifyState("已跳转支付宝，抢票成功！");
    }

    private void notifyState(String message) {
        Log.i(TAG, message);
        if (stateCallback != null) {
            stateCallback.onStateChange(currentStep, message);
        }
    }
}
