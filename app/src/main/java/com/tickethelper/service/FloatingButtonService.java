package com.tickethelper.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.tickethelper.R;
import com.tickethelper.engine.ClickStrategy;
import com.tickethelper.engine.GrabConfig;
import com.tickethelper.engine.GrabState;

public class FloatingButtonService extends Service {
    private static final String TAG = "FloatService";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private boolean isServiceEnabled = false;
    private boolean isGrabbing = false;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if ("START".equals(intent.getAction())) {
                showFloatingButton();
            } else if ("STOP".equals(intent.getAction())) {
                hideFloatingButton();
            } else if ("SET_ENABLED".equals(intent.getAction())) {
                isServiceEnabled = intent.getBooleanExtra("enabled", false);
                updateFloatingButtonState();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        hideFloatingButton();
        super.onDestroy();
    }

    private void showFloatingButton() {
        if (floatingView != null) return;

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);

        Point size = new Point();
        windowManager.getDefaultDisplay().getSize(size);

        params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params.format = PixelFormat.RGBA_8888;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = size.x - 160;
        params.y = size.y / 2;
        params.width = 120;
        params.height = 120;

        floatingView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(floatingView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;
                    if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                        toggleGrab();
                    }
                    return true;
            }
            return false;
        });

        try {
            windowManager.addView(floatingView, params);
            updateFloatingButtonState();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideFloatingButton() {
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            floatingView = null;
        }
    }

    private void updateFloatingButtonState() {
        if (floatingView == null) return;
        ImageView icon = floatingView.findViewById(R.id.float_icon);
        if (isServiceEnabled) {
            icon.setImageResource(R.drawable.ic_float_active);
        } else {
            icon.setImageResource(R.drawable.ic_float_inactive);
        }
    }

    private void toggleGrab() {
        GrabAccessibilityService service = GrabAccessibilityService.getInstance();
        if (service == null) return;

        if (isGrabbing) {
            service.stopGrabbing();
            isGrabbing = false;
            updateFloatIcon(false);
        } else {
            isGrabbing = true;
            updateFloatIcon(true);
            Intent intent = new Intent("com.tickethelper.START_GRAB");
            sendBroadcast(intent);
        }
    }

    private void updateFloatIcon(boolean grabbing) {
        if (floatingView == null) return;
        ImageView icon = floatingView.findViewById(R.id.float_icon);
        if (grabbing) {
            icon.setImageResource(R.drawable.ic_float_grabbing);
        } else if (isServiceEnabled) {
            icon.setImageResource(R.drawable.ic_float_active);
        } else {
            icon.setImageResource(R.drawable.ic_float_inactive);
        }
    }
}
