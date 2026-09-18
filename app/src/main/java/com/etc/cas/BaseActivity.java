package com.etc.cas;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.apply(newBase));
    }

    protected boolean ensureWifiPermission(Runnable granted) {
        granted.run();
        return true;
    }
}
