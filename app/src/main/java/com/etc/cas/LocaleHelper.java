package com.etc.cas;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {

    public static Locale localeFor(String fontKey) {
        switch (fontKey) {
            case Prefs.FONT_EN:
                return Locale.ENGLISH;
            case Prefs.FONT_TC:
                return Locale.TRADITIONAL_CHINESE;
            case Prefs.FONT_JP:
                return Locale.JAPANESE;
            default:
                return Locale.SIMPLIFIED_CHINESE;
        }
    }

    public static Context apply(Context context) {
        Locale locale = localeFor(Prefs.font(context));
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}
