package com.tickethelper.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class ScheduledTask {
    private static final String TAG = "ScheduledTask";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable task;
    private boolean running = false;

    public interface TaskCallback {
        void onTick(long remainingMillis);
        void onTimeUp();
    }

    // 倒计时到开售时间
    public void startCountdown(long targetTimeMillis, long clickInterval, TaskCallback callback) {
        stop();
        running = true;
        task = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                long now = System.currentTimeMillis();
                long remaining = targetTimeMillis - now;
                if (remaining <= 0) {
                    Log.i(TAG, "开售时间到！立即执行");
                    callback.onTimeUp();
                    running = false;
                } else {
                    callback.onTick(remaining);
                    long delay = Math.min(clickInterval, remaining);
                    handler.postDelayed(this, delay);
                }
            }
        };
        handler.post(task);
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    public boolean isRunning() {
        return running;
    }
}
