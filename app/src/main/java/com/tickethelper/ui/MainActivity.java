package com.tickethelper.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.tickethelper.R;
import com.tickethelper.engine.AppDetector;
import com.tickethelper.engine.GrabConfig;
import com.tickethelper.engine.GrabState;
import com.tickethelper.service.GrabAccessibilityService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements GrabState.Callback {
    private static final String TAG = "MainActivity";
    private static final String DAMAI_PACKAGE = "com.damai";

    // 服务状态
    private TextView tvServiceStatus;
    private MaterialButton btnOpenSettings;

    // 大麦检测
    private TextView tvDamaiStatus;
    private MaterialButton btnOpenDamai;
    private MaterialButton btnOpenDamaiInfo;

    // 抢票配置
    private Slider sliderHour, sliderMinute;
    private TextView tvTimeDisplay;
    private Slider sliderSession, sliderTicket;
    private TextView tvSessionVal, tvTicketVal;

    // 控制
    private MaterialButton btnStartScheduled, btnStartNow;
    private TextView tvCountdown, tvStepStatus;

    // 日志
    private TextView tvLog;
    private StringBuilder logBuilder = new StringBuilder();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private CountDownTimer countDownTimer;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initListeners();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service != null) {
            service.setCallback(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service != null) {
            service.setCallback(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        handler.removeCallbacksAndMessages(null);
    }

    private void initViews() {
        tvServiceStatus = findViewById(R.id.tv_service_status);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        tvDamaiStatus = findViewById(R.id.tv_damai_status);
        btnOpenDamai = findViewById(R.id.btn_open_damai);
        btnOpenDamaiInfo = findViewById(R.id.btn_open_damai_info);

        sliderHour = findViewById(R.id.slider_hour);
        sliderMinute = findViewById(R.id.slider_minute);
        tvTimeDisplay = findViewById(R.id.tv_time_display);

        sliderSession = findViewById(R.id.slider_session);
        sliderTicket = findViewById(R.id.slider_ticket);
        tvSessionVal = findViewById(R.id.tv_session_val);
        tvTicketVal = findViewById(R.id.tv_ticket_val);

        btnStartScheduled = findViewById(R.id.btn_start_scheduled);
        btnStartNow = findViewById(R.id.btn_start_now);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvStepStatus = findViewById(R.id.tv_step_status);
        tvLog = findViewById(R.id.tv_log);
    }

    private void initListeners() {
        btnOpenSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                Toast.makeText(this, "请找到「抢票助手」并开启", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "请手动进入: 设置→辅助功能→无障碍", Toast.LENGTH_LONG).show();
            }
        });

        btnOpenDamai.setOnClickListener(v -> {
            boolean ok = AppDetector.openApp(this, DAMAI_PACKAGE);
            if (!ok) {
                Toast.makeText(this, "未安装大麦，请先安装", Toast.LENGTH_SHORT).show();
            }
            refreshStatus();
        });

        btnOpenDamaiInfo.setOnClickListener(v -> {
            AppDetector.openAppInfo(this, DAMAI_PACKAGE);
        });

        // 时间滑块
        sliderHour.setLabelFormatter(value -> String.format("%02d:xx", (int) value));
        sliderMinute.setLabelFormatter(value -> String.format("xx:%02d", (int) value));
        updateTimeDisplay();

        sliderHour.addOnChangeListener((s, v, u) -> updateTimeDisplay());
        sliderMinute.addOnChangeListener((s, v, u) -> updateTimeDisplay());

        sliderSession.addOnChangeListener((s, v, u) ->
                tvSessionVal.setText(String.valueOf((int) v + 1)));
        sliderTicket.addOnChangeListener((s, v, u) ->
                tvTicketVal.setText(String.valueOf((int) v + 1)));

        tvSessionVal.setText("1");
        tvTicketVal.setText("1");

        // 定时抢票
        btnStartScheduled.setOnClickListener(v -> {
            if (isRunning) {
                stopGrabbing();
            } else {
                startScheduledGrab();
            }
        });

        // 立即抢票
        btnStartNow.setOnClickListener(v -> {
            if (isRunning) {
                stopGrabbing();
            } else {
                startGrabNow();
            }
        });

        // 默认时间设置为当前时间+1分钟（方便测试）
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 1);
        sliderHour.setValue(cal.get(Calendar.HOUR_OF_DAY));
        sliderMinute.setValue(cal.get(Calendar.MINUTE));
        updateTimeDisplay();

        tvSessionVal.setText("1");
        tvTicketVal.setText("1");
    }

    private void refreshStatus() {
        // 检查无障碍服务
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean serviceEnabled = false;
        if (am != null) {
            List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : list) {
                if (info.getId().contains("com.tickethelper")) {
                    serviceEnabled = true;
                    break;
                }
            }
        }

        if (serviceEnabled) {
            tvServiceStatus.setText("● 无障碍服务已开启");
            tvServiceStatus.setTextColor(getColor(R.color.success));
            btnOpenSettings.setVisibility(View.GONE);
        } else {
            tvServiceStatus.setText("○ 无障碍服务未开启");
            tvServiceStatus.setTextColor(getColor(R.color.warning));
            btnOpenSettings.setVisibility(View.VISIBLE);
        }

        // 检查大麦安装
        boolean damaiInstalled = AppDetector.isInstalled(this, DAMAI_PACKAGE);
        if (damaiInstalled) {
            tvDamaiStatus.setText("● 大麦APP已安装");
            tvDamaiStatus.setTextColor(getColor(R.color.success));
            btnOpenDamaiInfo.setVisibility(View.GONE);
        } else {
            tvDamaiStatus.setText("○ 大麦APP未安装");
            tvDamaiStatus.setTextColor(getColor(R.color.error));
            btnOpenDamaiInfo.setVisibility(View.VISIBLE);
        }

        if (!serviceEnabled) {
            btnStartScheduled.setEnabled(false);
            btnStartNow.setEnabled(false);
        } else {
            btnStartScheduled.setEnabled(true);
            btnStartNow.setEnabled(true);
        }
    }

    private void updateTimeDisplay() {
        int h = (int) sliderHour.getValue();
        int m = (int) sliderMinute.getValue();
        tvTimeDisplay.setText(String.format(Locale.CHINA, "开售时间: %02d:%02d", h, m));
    }

    private void startScheduledGrab() {
        int hour = (int) sliderHour.getValue();
        int minute = (int) sliderMinute.getValue();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long targetTime = cal.getTimeInMillis();
        long now = System.currentTimeMillis();

        // 如果时间已过，设为明天
        if (targetTime <= now) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            targetTime = cal.getTimeInMillis();
        }

        GrabConfig config = new GrabConfig();
        config.sessionIndex = (int) sliderSession.getValue();
        config.ticketIndex = (int) sliderTicket.getValue();

        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "无障碍服务未运行", Toast.LENGTH_SHORT).show();
            return;
        }

        service.setConfig(config);
        service.setCallback(this);
        service.startScheduledGrab(targetTime);
        isRunning = true;

        btnStartScheduled.setText("取消定时");
        addLog("已设置定时抢票: " + String.format("%02d:%02d", hour, minute));

        // 启动倒计时UI
        startCountdownUI(targetTime);
    }

    private void startGrabNow() {
        GrabConfig config = new GrabConfig();
        config.sessionIndex = (int) sliderSession.getValue();
        config.ticketIndex = (int) sliderTicket.getValue();

        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "无障碍服务未运行", Toast.LENGTH_SHORT).show();
            return;
        }

        service.setConfig(config);
        service.setCallback(this);
        service.startGrabbing();
        isRunning = true;

        btnStartNow.setText("停止抢票");
        addLog("立即抢票已启动");
        tvStepStatus.setText("正在抢票...");
    }

    private void stopGrabbing() {
        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service != null) service.stopGrabbing();
        isRunning = false;
        if (countDownTimer != null) countDownTimer.cancel();

        btnStartScheduled.setText("定时抢票");
        btnStartNow.setText("立即抢票");
        tvCountdown.setText("");
        addLog("已停止");
    }

    private void startCountdownUI(long targetTime) {
        long now = System.currentTimeMillis();
        long total = targetTime - now;
        if (total <= 0) return;

        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(total, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                String text = String.format(Locale.CHINA,
                        "距开售: %02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
                tvCountdown.setText(text);
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("开售中!");
            }
        }.start();
    }

    // ===== GrabState.Callback =====

    @Override
    public void onStateChange(int step, String message) {
        runOnUiThread(() -> {
            tvStepStatus.setText(message);
            addLog(message);

            if (step == GrabState.DONE) {
                tvStepStatus.setText("🎉 抢票成功！已跳转支付宝");
                isRunning = false;
                btnStartScheduled.setText("定时抢票");
                btnStartNow.setText("立即抢票");
                tvCountdown.setText("");
                if (countDownTimer != null) countDownTimer.cancel();
            } else if (step == GrabState.ERROR || step == GrabState.IDLE) {
                isRunning = false;
                btnStartScheduled.setText("定时抢票");
                btnStartNow.setText("立即抢票");
                tvCountdown.setText("");
            }
        });
    }

    private void addLog(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        logBuilder.insert(0, "[" + time + "] " + msg + "\n");
        if (logBuilder.length() > 3000) logBuilder.setLength(3000);
        tvLog.setText(logBuilder.toString());
    }
}
