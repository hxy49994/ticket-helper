package com.tickethelper.engine;

public class GrabConfig {
    public String damaiPackage = "com.damai";
    public int sessionIndex = 0;
    public int ticketIndex = 0;
    public int clickInterval = 200;
    public int maxRetry = 100;
    public long startTimeMillis = 0; // 开售时间戳

    // 抢票目标按钮文字（按优先级排序）
    public static final String[] BUY_BUTTONS = {"立即购买", "选座购买", "马上预订", "立即预定", "抢票"};
    public static final String[] SUBMIT_BUTTONS = {"提交订单", "立即支付", "去支付", "确认", "确定"};
    public static final String[] PAY_BUTTONS = {"支付宝", "确认支付", "支付"};
    public static final String[] CLOSE_BUTTONS = {"知道了", "关闭", "好的", "返回"};
}
