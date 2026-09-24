package com.etc.cas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QrScanActivity extends BaseActivity {

    private static final int REQ_CAMERA = 300;
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    private DecoratedBarcodeView barcodeView;
    private boolean cameraActive;
    private boolean launched;
    private boolean hintShown;
    private String pendingEtcasKey;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null) return;
            handleResult(result.getText());
        }

        @Override
        public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);

        barcodeView = findViewById(R.id.barcode_scanner);
        barcodeView.setStatusText("");
        barcodeView.decodeContinuous(callback);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        if (cameraActive) return;
        try {
            barcodeView.resume();
            cameraActive = true;
        } catch (Exception e) {
            Toast.makeText(this, R.string.qr_camera_error, Toast.LENGTH_LONG).show();
        }
    }

    private void stopCamera() {
        if (!cameraActive) return;
        try {
            barcodeView.pause();
        } catch (Exception ignored) {
        }
        cameraActive = false;
    }

    private String extractUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        Matcher m = URL_PATTERN.matcher(value);
        if (m.find()) {
            String u = m.group().trim();
            while (u.endsWith(".") || u.endsWith("，") || u.endsWith("。") || u.endsWith(",") || u.endsWith(")") || u.endsWith("）")) {
                u = u.substring(0, u.length() - 1);
            }
            return u;
        }
        String compact = value.replaceAll("\\s+", "");
        if (compact.startsWith("www.")) {
            return "https://" + compact;
        }
        if (!compact.contains("://") && compact.matches("[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/.*)?")) {
            return "https://" + compact;
        }
        return null;
    }

    private String detectEtcas(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (!value.toLowerCase().startsWith("etcas://")) return null;
        try {
            Uri u = Uri.parse(value);
            String ip = u.getQueryParameter("ip");
            if (ip == null || ip.isEmpty()) return null;
            String port = u.getQueryParameter("port");
            int p = 9170;
            if (port != null && !port.isEmpty()) {
                try { p = Integer.parseInt(port.trim()); } catch (Exception ignored) {}
            }
            String k = u.getQueryParameter("k");
            pendingEtcasKey = (k != null && !k.isEmpty()) ? k.trim() : null;
            return "http://" + ip.trim() + ":" + p + "/rootDesc.xml";
        } catch (Exception e) {
            return null;
        }
    }

    private void handleResult(String text) {
        if (launched) return;
        String etcas = detectEtcas(text);
        if (etcas != null) {
            launched = true;
            stopCamera();
            Toast.makeText(this, R.string.qr_etcas_found, Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, LocalCastActivity.class);
            i.putExtra("auto_search", true);
            i.putExtra("cast_url", etcas);
            if (pendingEtcasKey != null) i.putExtra("etcas_key", pendingEtcasKey);
            startActivity(i);
            finish();
            return;
        }
        String url = extractUrl(text);
        if (url != null) {
            launched = true;
            stopCamera();
            Intent i = new Intent(this, LinkCastActivity.class);
            i.putExtra("url", url);
            startActivity(i);
            finish();
        } else {
            showPairingHint();
        }
    }

    private void showPairingHint() {
        if (hintShown) return;
        hintShown = true;
        stopCamera();
        new AlertDialog.Builder(this)
                .setTitle(R.string.qr_title)
                .setMessage(R.string.qr_code_hint)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    hintShown = false;
                    if (hasCameraPermission()) startCamera();
                })
                .setPositiveButton(R.string.qr_search_device, (d, w) -> {
                    launched = true;
                    Intent i = new Intent(this, LocalCastActivity.class);
                    i.putExtra("auto_search", true);
                    startActivity(i);
                    finish();
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.qr_camera_denied, Toast.LENGTH_LONG).show();
                findViewById(R.id.tv_qr_hint).postDelayed(this::finish, 1200);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission()) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            barcodeView.decodeContinuous(null);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}
