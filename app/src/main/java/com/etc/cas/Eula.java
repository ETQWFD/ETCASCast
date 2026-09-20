package com.etc.cas;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

public class Eula {

    private static final String PREF = "etcas_prefs";
    private static final String KEY = "eula_accepted";

    public interface Done {
        void onAccept();
    }

    public static boolean accepted(Context ctx) {
        return sp(ctx).getBoolean(KEY, false);
    }

    public static void showIfNeeded(final Activity act, final Done done) {
        if (accepted(act)) {
            if (done != null) done.onAccept();
            return;
        }
        show(act, done);
    }

    public static void show(final Activity act, final Done done) {
        String text = act.getString(R.string.license_text) + "\n\n" + act.getString(R.string.eula_extra);
        new AlertDialog.Builder(act)
                .setTitle(R.string.eula_title)
                .setMessage(text)
                .setCancelable(false)
                .setPositiveButton(R.string.eula_agree, (d, w) -> {
                    sp(act).edit().putBoolean(KEY, true).apply();
                    if (done != null) done.onAccept();
                })
                .setNegativeButton(R.string.eula_exit, (d, w) -> act.finishAffinity())
                .show();
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
