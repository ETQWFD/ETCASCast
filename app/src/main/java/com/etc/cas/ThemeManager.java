package com.etc.cas;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;

public class ThemeManager {

    public static final int DANGER = 0xFFE5484D;
    private static final int DARK_BG = 0xFF11131A;

    public static int accent(Context c) {
        switch (Prefs.theme(c)) {
            case Prefs.THEME_SAKURA:
                return c.getColor(R.color.accent_sakura);
            case Prefs.THEME_ORANGE:
                return c.getColor(R.color.accent_orange);
            case Prefs.THEME_EMERALD:
                return c.getColor(R.color.accent_emerald);
            default:
                return c.getColor(R.color.accent_sea);
        }
    }

    public static int accentDeep(Context c) {
        switch (Prefs.theme(c)) {
            case Prefs.THEME_SAKURA:
                return c.getColor(R.color.accent_sakura_deep);
            case Prefs.THEME_ORANGE:
                return c.getColor(R.color.accent_orange_deep);
            case Prefs.THEME_EMERALD:
                return c.getColor(R.color.accent_emerald_deep);
            default:
                return c.getColor(R.color.accent_sea_deep);
        }
    }

    public static int accentSoft(Context c) {
        int accent = accent(c);
        if (isDark(c)) {
            return ColorUtils.blendARGB(accent, DARK_BG, 0.82f);
        }
        switch (Prefs.theme(c)) {
            case Prefs.THEME_SAKURA:
                return c.getColor(R.color.accent_sakura_soft);
            case Prefs.THEME_ORANGE:
                return c.getColor(R.color.accent_orange_soft);
            case Prefs.THEME_EMERALD:
                return c.getColor(R.color.accent_emerald_soft);
            default:
                return c.getColor(R.color.accent_sea_soft);
        }
    }

    public static boolean isDark(Context c) {
        String m = Prefs.mode(c);
        if (Prefs.MODE_DARK.equals(m)) return true;
        if (Prefs.MODE_LIGHT.equals(m)) return false;
        int uiMode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void applyNightMode(Context c) {
        String m = Prefs.mode(c);
        if (Prefs.MODE_DARK.equals(m)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (Prefs.MODE_LIGHT.equals(m)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static void apply(Activity a) {
        applyNightMode(a);
        Window w = a.getWindow();
        w.setStatusBarColor(accent(a));
        w.getDecorView().setSystemUiVisibility(0);
        ViewGroup root = a.findViewById(android.R.id.content);
        if (root != null) applyToTree(root, a);
    }

    private static void applyToTree(View v, Context c) {
        int accent = accent(c);
        String tag = (String) v.getTag();
        if (v instanceof MaterialButton) {
            MaterialButton b = (MaterialButton) v;
            if ("accent".equals(tag)) {
                b.setBackgroundTintList(ColorStateList.valueOf(accent));
                b.setTextColor(Color.WHITE);
            } else if ("danger".equals(tag)) {
                b.setBackgroundTintList(ColorStateList.valueOf(DANGER));
                b.setTextColor(Color.WHITE);
            } else {
                b.setBackgroundTintList(ColorStateList.valueOf(c.getColor(R.color.surface)));
                b.setTextColor(c.getColor(R.color.text_primary));
            }
        } else if (v instanceof ImageView) {
            ImageView iv = (ImageView) v;
            if ("icon_accent".equals(tag)) iv.setColorFilter(accent);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyToTree(g.getChildAt(i), c);
            }
        }
    }

    public static void colorHero(Context c, CardView hero) {
        if (hero != null) hero.setCardBackgroundColor(accent(c));
    }

    public static void colorModeCard(Context c, CardView card, View dot, boolean selected) {
        int accent = accent(c);
        if (selected) {
            card.setCardBackgroundColor(accentSoft(c));
            dot.setBackgroundTintList(ColorStateList.valueOf(accent));
        } else {
            card.setCardBackgroundColor(c.getColor(R.color.surface));
            dot.setBackgroundTintList(ColorStateList.valueOf(c.getColor(R.color.divider)));
        }
    }

    public static void colorChip(Context c, MaterialButton chip, boolean selected) {
        if (selected) {
            chip.setBackgroundTintList(ColorStateList.valueOf(accent(c)));
            chip.setTextColor(Color.WHITE);
        } else {
            chip.setBackgroundTintList(ColorStateList.valueOf(c.getColor(R.color.surface)));
            chip.setTextColor(c.getColor(R.color.text_primary));
        }
    }
}
