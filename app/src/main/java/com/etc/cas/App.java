package com.etc.cas;

import android.app.Application;
import android.content.res.Configuration;

import com.etc.cas.cast.LocalFileServer;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                File log = new File(getCacheDir(), "crash.log");
                FileWriter w = new FileWriter(log, true);
                w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                        + " [" + thread.getName() + "]\n");
                PrintWriter pw = new PrintWriter(w);
                e.printStackTrace(pw);
                pw.flush();
                pw.close();
            } catch (Exception ignored) {
            }
        });
        ThemeManager.applyNightMode(this);
        LocalFileServer.AppHolder.init(this);
        Configuration config = getResources().getConfiguration();
        Locale.setDefault(LocaleHelper.localeFor(Prefs.font(this)));
        if (config.getLocales().size() > 0) {
            Locale.setDefault(config.getLocales().get(0));
        }
    }
}
