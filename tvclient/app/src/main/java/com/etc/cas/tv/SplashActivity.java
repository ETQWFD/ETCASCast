package com.etc.cas.tv;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.etc.cas.tv.receiver.TvReceiverService;
import com.etc.cas.tv.util.NetUtil;

public class SplashActivity extends AppCompatActivity {

    private static final int TOTAL_SECONDS = 20;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProgressBar progress;
    private TextView tvDevice;
    private TextView tvNet;
    private boolean deviceDone;
    private boolean netDone;
    private int elapsed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progress = findViewById(R.id.progress);
        tvDevice = findViewById(R.id.tv_device);
        tvNet = findViewById(R.id.tv_net);
        progress.setMax(TOTAL_SECONDS);
        progress.setProgress(0);

        Intent svc = new Intent(this, TvReceiverService.class).setAction(TvReceiverService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);

        runDeviceStep();
        runNetworkStep();
        runProgress();
    }

    private void runDeviceStep() {
        tvDevice.setText(R.string.splash_device_doing);
        new Thread(() -> {
            boolean ok = false;
            try {
                String model = Build.MANUFACTURER + " " + Build.MODEL;
                ok = model != null && !model.trim().isEmpty();
                Thread.sleep(400);
            } catch (Exception ignored) {
            }
            boolean finalOk = ok;
            handler.post(() -> {
                deviceDone = true;
                tvDevice.setText((finalOk ? "✓ " : "✗ ") + getString(R.string.splash_device_done)
                        + " · " + Build.MANUFACTURER + " " + Build.MODEL
                        + " / Android " + Build.VERSION.RELEASE);
                tvDevice.setTextColor(getResources().getColor(finalOk ? R.color.ok : R.color.warn));
                tryEnter();
            });
        }, "etcas-tv-splash-dev").start();
    }

    private void runNetworkStep() {
        tvNet.setText(R.string.splash_net_doing);
        new Thread(() -> {
            boolean ok = false;
            String ip = "0.0.0.0";
            for (int i = 0; i < 10; i++) {
                ip = NetUtil.localIpv4();
                if (NetUtil.isNetworkConnected(getApplicationContext())
                        && ip != null && !"0.0.0.0".equals(ip)) {
                    ok = true;
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            String finalIp = ip;
            boolean finalOk = ok;
            handler.post(() -> {
                netDone = true;
                tvNet.setText((finalOk ? "✓ " : "✗ ") + getString(R.string.splash_net_done)
                        + " · IP " + finalIp);
                tvNet.setTextColor(getResources().getColor(finalOk ? R.color.ok : R.color.warn));
                tryEnter();
            });
        }, "etcas-tv-splash-net").start();
    }

    private void runProgress() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                elapsed++;
                progress.setProgress(elapsed);
                if (elapsed >= TOTAL_SECONDS) {
                    tryEnter();
                } else {
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    private void tryEnter() {
        if (elapsed >= TOTAL_SECONDS && deviceDone && netDone) {
            handler.removeCallbacksAndMessages(null);
            startActivity(new Intent(this, TvMainActivity.class));
            finish();
        }
    }
}
