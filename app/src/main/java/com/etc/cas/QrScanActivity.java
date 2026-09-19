package com.etc.cas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

public class QrScanActivity extends BaseActivity {

    private static final int REQ_CAMERA = 300;

    private DecoratedBarcodeView barcodeView;
    private boolean cameraActive;
    private boolean launched;

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

    private void handleResult(String text) {
        if (launched) return;
        String value = text.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            launched = true;
            stopCamera();
            Intent i = new Intent(this, LinkCastActivity.class);
            i.putExtra("url", value);
            startActivity(i);
            finish();
        } else {
            Toast.makeText(this, R.string.qr_result_invalid, Toast.LENGTH_SHORT).show();
        }
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
