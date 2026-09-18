package com.etc.cas;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class FontManager {

    private static final Map<String, Typeface> CACHE = new HashMap<>();

    public static Typeface font(Context c) {
        String key = Prefs.font(c);
        Typeface t = CACHE.get(key);
        if (t == null) {
            String file = fontFile(key);
            try {
                if (file != null) {
                    t = Typeface.createFromAsset(c.getAssets(), "fonts/" + file);
                }
            } catch (Throwable ignored) {
            }
            if (t == null) t = Typeface.DEFAULT;
            CACHE.put(key, t);
        }
        return t;
    }

    private static String fontFile(String key) {
        switch (key) {
            case Prefs.FONT_EN:
                return "Roboto-Regular.ttf";
            case Prefs.FONT_TC:
                return "NotoSansTC-Regular.ttf";
            case Prefs.FONT_JP:
                return "NotoSansJP-Regular.ttf";
            default:
                return "NotoSansSC-Regular.ttf";
        }
    }

    public static void apply(View root, Context c) {
        if (root == null) return;
        Typeface f = font(c);
        applyTree(root, f);
    }

    private static void applyTree(View v, Typeface f) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            int style = tv.getTypeface() != null ? tv.getTypeface().getStyle() : Typeface.NORMAL;
            tv.setTypeface(f, style);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyTree(g.getChildAt(i), f);
            }
        }
    }
}
