package com.etc.cas;

import android.app.Application;
import android.content.res.Configuration;

import com.etc.cas.cast.LocalFileServer;

import java.util.Locale;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyNightMode(this);
        LocalFileServer.AppHolder.init(this);
        Configuration config = getResources().getConfiguration();
        Locale.setDefault(LocaleHelper.localeFor(Prefs.font(this)));
        if (config.getLocales().size() > 0) {
            Locale.setDefault(config.getLocales().get(0));
        }
    }
}
