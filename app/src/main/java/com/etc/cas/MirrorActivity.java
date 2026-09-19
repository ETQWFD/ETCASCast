package com.etc.cas;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.etc.cas.cast.LocalFileServer;
import com.etc.cas.discovery.CastDevice;
import com.etc.cas.discovery.DeviceDiscoverer;
import com.etc.cas.util.DevicePickDialog;

public class MirrorActivity extends BaseActivity {

    private static final int REQ_PROJECTION = 200;

    private boolean mirroring;
    private CastDevice pendingDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_mirror).setOnClickListener(v -> {
            if (mirroring) {
                stopMirror();
            } else {
                requestProjection();
            }
        });
        findViewById(R.id.btn_cast).setOnClickListener(v -> discoverAndPick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        FontManager.apply(findViewById(android.R.id.content), this);
    }

    private void requestProjection() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROJECTION && resultCode == RESULT_OK && data != null) {
            startMirrorService(resultCode, data);
        } else if (requestCode == REQ_PROJECTION) {
            Toast.makeText(this, R.string.mirror_permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void startMirrorService(int code, Intent data) {
        LocalFileServer.start(this, null, null);

        Intent i = new Intent(this, MirrorService.class);
        i.setAction(MirrorService.ACTION_START);
        i.putExtra("code", code);
        i.putExtra("data", data);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.mirror_permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        mirroring = true;
        ((TextView) findViewById(R.id.tv_mirror_status)).setText(R.string.mirror_running);
        findViewById(R.id.tv_mirror_live).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.btn_mirror)).setText(R.string.mirror_stop);
        TextView url = findViewById(R.id.tv_mirror_url);
        url.setVisibility(View.VISIBLE);
        url.setText(getString(R.string.mirror_browser_hint) + "\n" + LocalFileServer.mirrorPageUrl(this));

        if (pendingDevice != null) {
            CastDevice d = pendingDevice;
            pendingDevice = null;
            showConnected(d);
        }
    }

    private void stopMirror() {
        stopService(new Intent(this, MirrorService.class));
        mirroring = false;
        ((TextView) findViewById(R.id.tv_mirror_status)).setText(R.string.mirror_start);
        findViewById(R.id.tv_mirror_live).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.btn_mirror)).setText(R.string.mirror_start);
        findViewById(R.id.tv_mirror_url).setVisibility(View.GONE);
    }

    private void discoverAndPick() {
        try {
            AlertDialog dialog = DevicePickDialog.show(this, null, callback());
            if (dialog == null) return;
            new DeviceDiscoverer().start(this, (devices, stillSearching) -> runOnUiThread(() -> {
                try {
                    DevicePickDialog.update(dialog, devices, stillSearching, callback());
                } catch (Exception ignored) {
                }
            }));
        } catch (Exception e) {
            Toast.makeText(this, R.string.device_search_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void showConnected(CastDevice d) {
        String url = LocalFileServer.mirrorPageUrl(MirrorActivity.this);
        new AlertDialog.Builder(MirrorActivity.this)
                .setTitle(R.string.device_connected)
                .setMessage(getString(R.string.session_casting_to) + " " + d.name + "\n\n"
                        + getString(R.string.mirror_browser_hint) + "\n" + url)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private DevicePickDialog.Callback callback() {
        return new DevicePickDialog.Callback() {
            @Override
            public void onPick(CastDevice d) {
                if (!mirroring) {
                    pendingDevice = d;
                    requestProjection();
                } else {
                    showConnected(d);
                }
            }

            @Override
            public void onRefresh() {
                discoverAndPick();
            }

            @Override
            public void onManual() {
                manualAdd();
            }
        };
    }

    private void manualAdd() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.input_ip_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.manual_add)
                .setView(input)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    String ip = input.getText().toString().trim();
                    if (ip.isEmpty()) return;
                    Toast.makeText(this, R.string.probing, Toast.LENGTH_SHORT).show();
                    new DeviceDiscoverer().probeIp(this, ip, (devices, stillSearching) -> runOnUiThread(() -> {
                        if (devices == null || devices.isEmpty()) {
                            Toast.makeText(MirrorActivity.this, R.string.device_search_fail, Toast.LENGTH_SHORT).show();
                        } else {
                            AlertDialog dialog = DevicePickDialog.show(MirrorActivity.this, devices, callback());
                            if (dialog == null) callback().onPick(devices.get(0));
                        }
                    }));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, MirrorService.class));
        super.onDestroy();
    }
}
