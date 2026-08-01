package com.tickethelper.engine;

public class GrabState {
    public static final int STEP_IDLE = -1;
    public static final int STEP_WAIT = 0;
    public static final int STEP_BUY = 1;
    public static final int STEP_SUBMIT = 2;
    public static final int STEP_PAY = 3;
    public static final int STEP_DONE = 4;

    public static String getStepName(int step) {
        switch (step) {
            case STEP_WAIT: return "等待开始";
            case STEP_BUY: return "点击购买";
            case STEP_SUBMIT: return "提交订单";
            case STEP_PAY: return "选择支付";
            case STEP_DONE: return "支付跳转";
            default: return "未启动";
        }
    }

    public interface StateCallback {
        void onStateChanged(int step, String message);
    }
}
