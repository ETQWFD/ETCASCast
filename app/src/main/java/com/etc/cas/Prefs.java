package com.etc.cas;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    public static final String THEME_SAKURA = "sakura";
    public static final String THEME_SEA = "sea";
    public static final String THEME_ORANGE = "orange";
    public static final String THEME_EMERALD = "emerald";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";

    public static final String FONT_SC = "sc";
    public static final String FONT_EN = "en";
    public static final String FONT_TC = "tc";
    public static final String FONT_JP = "jp";

    private static final String NAME = "etcas_prefs";
    private static final String K_THEME = "theme";
    private static final String K_MODE = "mode";
    private static final String K_FONT = "font";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String theme(Context c) {
        return sp(c).getString(K_THEME, THEME_SEA);
    }

    public static void setTheme(Context c, String v) {
        sp(c).edit().putString(K_THEME, v).apply();
    }

    public static String mode(Context c) {
        return sp(c).getString(K_MODE, MODE_LIGHT);
    }

    public static void setMode(Context c, String v) {
        sp(c).edit().putString(K_MODE, v).apply();
    }

    public static String font(Context c) {
        return sp(c).getString(K_FONT, FONT_SC);
    }

    public static void setFont(Context c, String v) {
        sp(c).edit().putString(K_FONT, v).apply();
    }
}
