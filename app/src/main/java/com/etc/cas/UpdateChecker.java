package com.etc.cas;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    public static final String REPO = "ETQWFD/ETCASCast";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void check(final Context ctx) {
        Toast.makeText(ctx, R.string.update_checking, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "ETCASCast/1.4");
                int code = conn.getResponseCode();
                if (code != 200) {
                    fail(ctx);
                    return;
                }
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                JSONObject json = new JSONObject(bos.toString("UTF-8"));
                String tag = json.optString("tag_name", "");
                String body = json.optString("body", "");
                String download = null;
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name", "").endsWith(".apk")) {
                            download = a.optString("browser_download_url");
                            break;
                        }
                    }
                }
                final String fTag = tag;
                final String fBody = body;
                final String fDownload = download;
                boolean newer = compareVersion(tag, BuildConfig.VERSION_NAME) > 0;
                MAIN.post(() -> {
                    if (!newer) {
                        Toast.makeText(ctx, R.string.update_latest, Toast.LENGTH_SHORT).show();
                    } else {
                        new AlertDialog.Builder(ctx)
                                .setTitle(ctx.getString(R.string.update_found) + " " + fTag)
                                .setMessage(fBody == null || fBody.isEmpty() ? fTag : fBody)
                                .setPositiveButton(R.string.update_download, (d, w) -> {
                                    if (fDownload != null) {
                                        ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fDownload)));
                                    }
                                })
                                .setNegativeButton(R.string.update_later, null)
                                .show();
                    }
                });
            } catch (Exception e) {
                fail(ctx);
            }
        }, "etcas-update").start();
    }

    private static void fail(final Context ctx) {
        MAIN.post(() -> Toast.makeText(ctx, R.string.update_fail, Toast.LENGTH_SHORT).show());
    }

    private static int compareVersion(String a, String b) {
        String[] pa = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] pb = b.replaceAll("[^0-9.]", "").split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int x = i < pa.length ? parseInt(pa[i]) : 0;
            int y = i < pb.length ? parseInt(pb[i]) : 0;
            if (x != y) return x - y;
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
