package com.etc.cas;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.etc.cas.discovery.CastDevice;
import com.etc.cas.discovery.DeviceDiscoverer;
import com.etc.cas.util.DevicePickDialog;
import com.etc.cas.util.VideoInfo;
import com.etc.cas.util.VideoInfoFetcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LinkCastActivity extends BaseActivity {

    private VideoInfo videoInfo;
    private CastDevice pendingDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_cast);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_fetch).setOnClickListener(v -> fetchInfo());
        findViewById(R.id.btn_cast).setOnClickListener(v -> startCastFlow());

        String preset = getIntent().getStringExtra("url");
        if (preset != null && !preset.isEmpty()) {
            ((EditText) findViewById(R.id.et_link)).setText(preset);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        FontManager.apply(findViewById(android.R.id.content), this);
    }

    private void fetchInfo() {
        EditText et = findViewById(R.id.et_link);
        String url = et.getText().toString().trim();
        if (!url.startsWith("http")) {
            Toast.makeText(this, R.string.link_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        findViewById(R.id.btn_fetch).setEnabled(false);
        ((TextView) findViewById(R.id.btn_fetch)).setText(R.string.link_fetching);

        new Thread(() -> {
            VideoInfo info = VideoInfoFetcher.fetch(url);
            runOnUiThread(() -> {
                findViewById(R.id.btn_fetch).setEnabled(true);
                ((TextView) findViewById(R.id.btn_fetch)).setText(R.string.link_fetch);
                if (info == null) {
                    Toast.makeText(this, R.string.link_fetch_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                videoInfo = info;
                LinearLayout panel = findViewById(R.id.panel_info);
                panel.setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.tv_link_title)).setText(info.title);
                ((TextView) findViewById(R.id.tv_link_type)).setText(
                        getString(info.directMedia ? R.string.link_media_direct : R.string.link_media_page));

                if (info.imageUrl != null && !info.imageUrl.isEmpty()) {
                    loadThumb(info.imageUrl);
                } else {
                    findViewById(R.id.img_thumb).setVisibility(View.GONE);
                }

                CastDevice d = pendingDevice;
                if (d != null) {
                    pendingDevice = null;
                    launchSession(d);
                }
            });
        }).start();
    }

    private void loadThumb(String imageUrl) {
        new Thread(() -> {
            try {
                URL u = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 ETCASCast/1.4");
                conn.setRequestMethod("GET");
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                conn.disconnect();
                byte[] data = bos.toByteArray();
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                runOnUiThread(() -> {
                    if (bmp != null) {
                        ImageView iv = findViewById(R.id.img_thumb);
                        iv.setImageBitmap(bmp);
                        iv.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void startCastFlow() {
        discoverAndPick();
    }

    private void launchSession(CastDevice d) {
        Intent i = new Intent(LinkCastActivity.this, CastSessionActivity.class);
        i.putExtra("mode", "link");
        i.putExtra("uri", videoInfo.url);
        i.putExtra("mime", videoInfo.contentType == null ? "" : videoInfo.contentType);
        i.putExtra("title", videoInfo.title == null ? "" : videoInfo.title);
        i.putExtra("isDirect", videoInfo.directMedia);
        i.putExtra("referer", videoInfo.referer == null ? "" : videoInfo.referer);
        i.putExtra("device", d);
        startActivity(i);
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

    private DevicePickDialog.Callback callback() {
        return new DevicePickDialog.Callback() {
            @Override
            public void onPick(CastDevice d) {
                com.etc.cas.cast.PairGate.request(LinkCastActivity.this, d, dev -> {
                    if (videoInfo == null) {
                        pendingDevice = dev;
                        EditText et = findViewById(R.id.et_link);
                        String url = et.getText().toString().trim();
                        if (!url.startsWith("http")) {
                            Toast.makeText(LinkCastActivity.this, R.string.link_empty, Toast.LENGTH_SHORT).show();
                            et.requestFocus();
                            return;
                        }
                        fetchInfo();
                    } else {
                        launchSession(dev);
                    }
                });
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
                            Toast.makeText(LinkCastActivity.this, R.string.device_search_fail, Toast.LENGTH_SHORT).show();
                        } else {
                            AlertDialog dialog = DevicePickDialog.show(LinkCastActivity.this, devices, callback());
                            if (dialog == null) callback().onPick(devices.get(0));
                        }
                    }));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
