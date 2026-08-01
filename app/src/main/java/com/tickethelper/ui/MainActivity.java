package com.tickethelper.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.tickethelper.R;
import com.tickethelper.engine.GrabConfig;
import com.tickethelper.engine.GrabState;
import com.tickethelper.service.FloatingButtonService;
import com.tickethelper.service.GrabAccessibilityService;

import java.util.List;

public class MainActivity extends AppCompatActivity implements GrabState.StateCallback {
    private static final String TAG = "MainActivity";
    private static final String DAMAI_PACKAGE = "com.damai";
    private static final int OVERLAY_PERMISSION_REQUEST = 1001;

    private TextView tvServiceStatus;
    private TextView tvStepStatus;
    private MaterialButton btnStartStop;
    private MaterialButton btnOpenDamai;
    private MaterialButton btnOpenSettings;
    private MaterialButton btnFloatToggle;
    private TextView tvLog;
    private Slider sliderSession;
    private Slider sliderTicket;
    private Slider sliderInterval;
    private Slider sliderRetry;
    private TextView tvSessionVal;
    private TextView tvTicketVal;
    private TextView tvIntervalVal;
    private TextView tvRetryVal;
    private StringBuilder logBuilder = new StringBuilder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initListeners();
        checkAccessibilityService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessibilityService();
        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service != null) {
            service.setStateCallback(this);
            updateServiceStatus(true, service.isGrabbing());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service != null) {
            service.setStateCallback(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        stopFloatingService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingService();
                addLog("悬浮窗权限已授予，悬浮按钮已显示");
            } else {
                Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initViews() {
        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvStepStatus = findViewById(R.id.tv_step_status);
        btnStartStop = findViewById(R.id.btn_start_stop);
        btnOpenDamai = findViewById(R.id.btn_open_damai);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        btnFloatToggle = findViewById(R.id.btn_float_toggle);
        tvLog = findViewById(R.id.tv_log);
        sliderSession = findViewById(R.id.slider_session);
        sliderTicket = findViewById(R.id.slider_ticket);
        sliderInterval = findViewById(R.id.slider_interval);
        sliderRetry = findViewById(R.id.slider_retry);
        tvSessionVal = findViewById(R.id.tv_session_val);
        tvTicketVal = findViewById(R.id.tv_ticket_val);
        tvIntervalVal = findViewById(R.id.tv_interval_val);
        tvRetryVal = findViewById(R.id.tv_retry_val);
    }

    private void initListeners() {
        btnOpenSettings.setOnClickListener(v -> openAccessibilitySettings());
        btnOpenDamai.setOnClickListener(v -> openDamai());
        btnStartStop.setOnClickListener(v -> toggleGrabbing());
        btnFloatToggle.setOnClickListener(v -> toggleFloatingButton());

        sliderSession.addOnChangeListener((slider, value, fromUser) ->
                tvSessionVal.setText(String.valueOf((int) value + 1)));
        sliderTicket.addOnChangeListener((slider, value, fromUser) ->
                tvTicketVal.setText(String.valueOf((int) value + 1)));
        sliderInterval.addOnChangeListener((slider, value, fromUser) ->
                tvIntervalVal.setText(String.valueOf((int) value)));
        sliderRetry.addOnChangeListener((slider, value, fromUser) ->
                tvRetryVal.setText(String.valueOf((int) value)));

        tvSessionVal.setText("1");
        tvTicketVal.setText("1");
        tvIntervalVal.setText("500");
        tvRetryVal.setText("30");
    }

    private void checkAccessibilityService() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            updateServiceStatus(false, false);
            return;
        }
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        boolean found = false;
        ComponentName targetComponent = new ComponentName(this, GrabAccessibilityService.class);
        for (AccessibilityServiceInfo info : enabledServices) {
            String id = info.getId();
            if (id != null && id.equals(targetComponent.flattenToString())) {
                found = true;
                break;
            }
        }
        updateServiceStatus(found, false);
        btnOpenSettings.setVisibility(found ? View.GONE : View.VISIBLE);
        // 通知悬浮服务状态变化
        Intent intent = new Intent(this, FloatingButtonService.class);
        intent.setAction("SET_ENABLED");
        intent.putExtra("enabled", found);
        startService(intent);
    }

    private void updateServiceStatus(boolean enabled, boolean grabbing) {
        runOnUiThread(() -> {
            if (enabled) {
                tvServiceStatus.setText("无障碍服务已开启 ✓");
                tvServiceStatus.setTextColor(getColor(R.color.success));
                btnStartStop.setEnabled(true);
                btnStartStop.setText(grabbing ? "停止抢票" : "开始抢票");
                btnStartStop.setBackgroundColor(getColor(grabbing ? R.color.error : R.color.damai_red));
            } else {
                tvServiceStatus.setText("无障碍服务未开启 ✗");
                tvServiceStatus.setTextColor(getColor(R.color.warning));
                btnStartStop.setText("开始抢票");
                btnStartStop.setEnabled(false);
                btnStartStop.setBackgroundColor(getColor(R.color.divider));
            }
        });
    }

    private void toggleGrabbing() {
        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "无障碍服务未运行，请先在设置中开启", Toast.LENGTH_SHORT).show();
            return;
        }

        if (service.isGrabbing()) {
            service.stopGrabbing();
            updateServiceStatus(true, false);
            addLog("已手动停止抢票");
        } else {
            GrabConfig config = new GrabConfig();
            config.sessionIndex = (int) sliderSession.getValue();
            config.ticketIndex = (int) sliderTicket.getValue();
            config.clickInterval = (int) sliderInterval.getValue();
            config.maxRetry = (int) sliderRetry.getValue();

            service.setStateCallback(this);
            service.startGrabbing(config);
            updateServiceStatus(true, true);
            addLog("启动抢票: 场次" + (config.sessionIndex + 1)
                    + " 票档" + (config.ticketIndex + 1)
                    + " 间隔" + config.clickInterval + "ms");
        }
    }

    private void toggleFloatingButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
                return;
            }
        }
        // 开启/关闭悬浮按钮
        if (btnFloatToggle.getText().toString().contains("开启")) {
            startFloatingService();
        } else {
            stopFloatingService();
        }
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingButtonService.class);
        intent.setAction("START");
        startService(intent);
        btnFloatToggle.setText("关闭悬浮按钮");
        addLog("悬浮按钮已显示");
    }

    private void stopFloatingService() {
        Intent intent = new Intent(this, FloatingButtonService.class);
        intent.setAction("STOP");
        startService(intent);
        btnFloatToggle.setText("开启悬浮按钮");
        addLog("悬浮按钮已隐藏");
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "请找到「抢票助手」并开启无障碍服务", Toast.LENGTH_LONG).show();
    }

    private void openDamai() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(DAMAI_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                addLog("已打开大麦APP");
            } else {
                Toast.makeText(this, "未安装大麦APP", Toast.LENGTH_SHORT).show();
                addLog("未找到大麦APP");
            }
        } catch (Exception e) {
            Log.e(TAG, "打开大麦失败", e);
            Toast.makeText(this, "打开大麦失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStateChanged(int step, String message) {
        runOnUiThread(() -> {
            tvStepStatus.setText(GrabState.getStepName(step));
            addLog(message);
            if (step == GrabState.STEP_DONE) {
                Toast.makeText(this, "已跳转支付宝，抢票完成！", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addLog(String message) {
        String time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
                .format(new java.util.Date());
        String logLine = "[" + time + "] " + message + "\n";
        logBuilder.insert(0, logLine);
        if (logBuilder.length() > 5000) {
            logBuilder.setLength(5000);
        }
        tvLog.setText(logBuilder.toString());
    }
}
