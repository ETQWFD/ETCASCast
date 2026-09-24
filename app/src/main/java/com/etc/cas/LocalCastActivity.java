package com.etc.cas;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.etc.cas.cast.LocalFileServer;
import com.etc.cas.cast.PairGate;
import com.etc.cas.discovery.CastDevice;
import com.etc.cas.discovery.DeviceDiscoverer;
import com.etc.cas.util.DevicePickDialog;
import com.etc.cas.util.M4sConverter;

import java.io.File;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.List;

public class LocalCastActivity extends BaseActivity {

    private static final int REQ_PICK = 100;

    private Uri fileUri;
    private String fileName;
    private String fileMime;
    private long fileSize = -1;
    private CastDevice pendingDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_cast);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pick).setOnClickListener(v -> pickFile());
        findViewById(R.id.btn_cast).setOnClickListener(v -> startCastFlow());

        if (getIntent() != null && getIntent().getBooleanExtra("auto_search", false)) {
            String castUrl = getIntent().getStringExtra("cast_url");
            if (castUrl != null && !castUrl.isEmpty()) {
                findViewById(R.id.btn_cast).postDelayed(() -> connectByUrl(castUrl), 400);
            } else {
                findViewById(R.id.btn_cast).postDelayed(this::discoverAndPick, 400);
            }
        }
    }

    private void connectByUrl(String castUrl) {
        Toast.makeText(this, R.string.probing, Toast.LENGTH_SHORT).show();
        final String scanKey = getIntent().getStringExtra("etcas_key");
        new DeviceDiscoverer().probeUrl(this, castUrl, (devices, stillSearching) -> runOnUiThread(() -> {
            if (devices == null || devices.isEmpty()) {
                if (!stillSearching) {
                    Toast.makeText(this, R.string.device_search_fail, Toast.LENGTH_SHORT).show();
                }
            } else {
                pendingDevice = devices.get(0);
                if (scanKey != null && !scanKey.isEmpty() && pendingDevice.key == null) {
                    pendingDevice.key = scanKey;
                }
                Toast.makeText(this, getString(R.string.qr_etcas_connected) + " · " + pendingDevice.name,
                        Toast.LENGTH_SHORT).show();
                PairGate.requestQuick(this, pendingDevice, d -> {
                    if (fileUri == null) pickFile();
                });
            }
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        FontManager.apply(findViewById(android.R.id.content), this);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            fileUri = uri;
            fileMime = getContentResolver().getType(uri);
            queryMeta(uri);

            LinearLayout panel = findViewById(R.id.panel_file);
            panel.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tv_file_name)).setText(fileName);
            ((TextView) findViewById(R.id.tv_file_info)).setText(buildInfo());

            if (M4sConverter.isM4s(fileName)) {
                ((TextView) findViewById(R.id.tv_file_info)).setText(R.string.local_converting);
                convertM4s(uri, panel);
            } else {
                showPreview(uri, panel);
                CastDevice d = pendingDevice;
                if (d != null) {
                    pendingDevice = null;
                    launchSession(d);
                }
            }
        }
    }

    private void convertM4s(Uri uri, LinearLayout panel) {
        final String name = fileName;
        new Thread(() -> {
            File mp4 = M4sConverter.convert(this, uri, name);
            runOnUiThread(() -> {
                if (mp4 != null && mp4.length() > 0) {
                    fileUri = Uri.fromFile(mp4);
                    fileMime = "video/mp4";
                    fileSize = mp4.length();
                    ((TextView) findViewById(R.id.tv_file_name)).setText(name + "（MP4）");
                    ((TextView) findViewById(R.id.tv_file_info)).setText(R.string.local_converted);
                } else {
                    ((TextView) findViewById(R.id.tv_file_info)).setText(buildInfo());
                    Toast.makeText(this, R.string.local_convert_fail, Toast.LENGTH_LONG).show();
                }
                CastDevice d = pendingDevice;
                if (d != null) {
                    pendingDevice = null;
                    launchSession(d);
                }
            });
        }, "etcas-m4s").start();
    }

    private void showPreview(Uri uri, LinearLayout panel) {
        ImageView preview = findViewById(R.id.img_preview);
        if (fileMime != null && fileMime.startsWith("image/")) {
            loadImagePreview(uri, preview);
        } else {
            preview.setVisibility(View.GONE);
        }
    }

    private void queryMeta(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = c.getColumnIndex(OpenableColumns.SIZE);
                if (nameIdx >= 0) fileName = c.getString(nameIdx);
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) fileSize = c.getLong(sizeIdx);
            }
        } catch (Exception ignored) {
        }
        if (fileName == null || fileName.isEmpty()) fileName = uri.getLastPathSegment();
    }

    private String buildInfo() {
        String type = fileMime == null ? "unknown" : fileMime;
        String size = fileSize > 0 ? formatSize(fileSize) : "?";
        return type + " · " + size;
    }

    private void loadImagePreview(Uri uri, ImageView preview) {
        new Thread(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(is, null, opts);
                }
                int sample = 1;
                while (opts.outWidth / sample > 1400 || opts.outHeight / sample > 1400) {
                    sample *= 2;
                }
                Bitmap bmp;
                try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    bmp = BitmapFactory.decodeStream(is2, null, opts);
                }
                final Bitmap fb = bmp;
                runOnUiThread(() -> {
                    if (fb != null) {
                        preview.setImageBitmap(fb);
                        preview.setVisibility(View.VISIBLE);
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
        String base = LocalFileServer.start(this, fileUri, fileMime);
        if (base == null) {
            Toast.makeText(this, R.string.device_connect_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(LocalCastActivity.this, CastSessionActivity.class);
        i.putExtra("mode", "local");
        i.putExtra("uri", fileUri.toString());
        i.putExtra("mime", fileMime == null ? "" : fileMime);
        i.putExtra("title", fileName == null ? "" : fileName);
        i.putExtra("isDirect", true);
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
                PairGate.request(LocalCastActivity.this, d, dev -> {
                    if (fileUri == null) {
                        pendingDevice = dev;
                        pickFile();
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
                            Toast.makeText(LocalCastActivity.this, R.string.device_search_fail, Toast.LENGTH_SHORT).show();
                        } else {
                            AlertDialog dialog = DevicePickDialog.show(LocalCastActivity.this, devices, callback());
                            if (dialog == null) callback().onPick(devices.get(0));
                        }
                    }));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return new DecimalFormat("#.#").format(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return new DecimalFormat("#.##").format(bytes / 1024.0 / 1024.0) + " MB";
        return new DecimalFormat("#.##").format(bytes / 1024.0 / 1024.0 / 1024.0) + " GB";
    }
}
