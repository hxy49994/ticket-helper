package com.tickethelper.engine;

public class GrabState {
    public static final int IDLE = -1;
    public static final int WAITING = 0;
    public static final int CLICK_BUY = 1;
    public static final int CLICK_SUBMIT = 2;
    public static final int CLICK_PAY = 3;
    public static final int DONE = 4;
    public static final int ERROR = 5;

    public static String getStepName(int step) {
        switch (step) {
            case IDLE: return "待命";
            case WAITING: return "等待开售";
            case CLICK_BUY: return "点击购买";
            case CLICK_SUBMIT: return "提交订单";
            case CLICK_PAY: return "跳转支付";
            case DONE: return "抢票成功!";
            case ERROR: return "出错";
            default: return "未知";
        }
    }

    public interface Callback {
        void onStateChange(int step, String message);
    }
}
