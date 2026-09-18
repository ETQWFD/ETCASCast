package com.etc.cas;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public abstract class BaseActivity extends AppCompatActivity {

    private static final int REQ_WIFI = 1000;
    private Runnable pendingPermissionAction;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.apply(newBase));
    }

    protected boolean ensureWifiPermission(Runnable granted) {
        String permission = wifiPermission();
        if (permission == null) {
            granted.run();
            return true;
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            granted.run();
            return true;
        }
        pendingPermissionAction = granted;
        requestPermissions(new String[]{permission}, REQ_WIFI);
        return false;
    }

    private String wifiPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return Manifest.permission.NEARBY_WIFI_DEVICES;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            return Manifest.permission.ACCESS_FINE_LOCATION;
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WIFI) {
            Runnable action = pendingPermissionAction;
            pendingPermissionAction = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (action != null) action.run();
            } else {
                Toast.makeText(this, R.string.permission_wifi_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
