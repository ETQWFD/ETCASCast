package com.etc.cas;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.etc.cas.cast.LocalFileServer;
import com.etc.cas.discovery.CastDevice;
import com.etc.cas.discovery.DeviceDiscoverer;
import com.etc.cas.util.DevicePickDialog;

import java.lang.ref.WeakReference;

public class MirrorActivity extends BaseActivity {

    private static final int REQ_PROJECTION = 200;

    private boolean mirroring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_mirror).setOnClickListener(v -> requestProjection());
        findViewById(R.id.btn_cast).setOnClickListener(v -> {
            if (!mirroring) {
                Toast.makeText(this, R.string.mirror_permission_denied, Toast.LENGTH_SHORT).show();
            } else {
                ensureWifiPermission(this::discoverAndPick);
            }
        });
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

        SurfaceView sv = findViewById(R.id.sv_preview);
        SurfaceHolder holder = sv.getHolder();
        MirrorService.previewHolder = new WeakReference<>(holder);

        Intent i = new Intent(this, MirrorService.class);
        i.setAction(MirrorService.ACTION_START);
        i.putExtra("code", code);
        i.putExtra("data", data);
        startForegroundService(i);

        mirroring = true;
        ((TextView) findViewById(R.id.tv_mirror_status)).setText(R.string.mirror_running);
        TextView url = findViewById(R.id.tv_mirror_url);
        url.setVisibility(View.VISIBLE);
        url.setText(getString(R.string.mirror_browser_hint) + "\n" + LocalFileServer.mirrorUrl(this));
    }

    private void discoverAndPick() {
        AlertDialog dialog = DevicePickDialog.show(this, null, callback());
        new DeviceDiscoverer().start(this, devices -> runOnUiThread(() ->
                DevicePickDialog.update(dialog, devices, callback())));
    }

    private DevicePickDialog.Callback callback() {
        return new DevicePickDialog.Callback() {
            @Override
            public void onPick(CastDevice d) {
                String url = LocalFileServer.mirrorUrl(MirrorActivity.this);
                new AlertDialog.Builder(MirrorActivity.this)
                        .setTitle(R.string.device_connected)
                        .setMessage(getString(R.string.session_casting_to) + " " + d.name + "\n\n"
                                + getString(R.string.mirror_browser_hint) + "\n" + url)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }

            @Override
            public void onRefresh() {
                discoverAndPick();
            }
        };
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, MirrorService.class));
        MirrorService.previewHolder = null;
        super.onDestroy();
    }
}
