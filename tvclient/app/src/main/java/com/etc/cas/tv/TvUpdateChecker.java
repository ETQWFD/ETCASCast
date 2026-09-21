package com.etc.cas.tv;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TvUpdateChecker {

    private static final String REPO = "ETQWFD/ETCASCastTV";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    public static void checkStart(final Activity act) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(API).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "ETCASCastTV");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                if (conn.getResponseCode() != 200) return;
                String body = readAll(conn.getInputStream());
                JSONObject jo = new JSONObject(body);
                String tag = jo.optString("tag_name", "").replace("v", "").trim();
                String notes = jo.optString("body", "");
                String apkUrl = null;
                String apkName = null;
                long apkId = 0L;
                JSONArray assets = jo.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String name = a.optString("name", "").toLowerCase();
                        if (name.startsWith("etcascast-tv-v") && name.endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", "");
                            apkName = a.optString("name", "");
                            apkId = a.optLong("id", 0L);
                            break;
                        }
                    }
                }
                if (apkUrl == null) return;
                String cur = currentVersion(act);
                if (compare(tag, cur) > 0) {
                    final String url = apkUrl;
                    final String fn = apkName;
                    final long id = apkId;
                    final String ver = tag;
                    final String nb = notes;
                    act.runOnUiThread(() -> showDialog(act, ver, nb, url, fn, id));
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "etcas-tv-update-check").start();
    }

    private static void showDialog(Activity act, String version, String notes,
                                   final String url, final String name, final long assetId) {
        String msg = act.getString(R.string.update_found) + " v" + version;
        if (notes != null && !notes.trim().isEmpty()) msg += "\n\n" + notes.trim();
        new AlertDialog.Builder(act)
                .setTitle(R.string.update_title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(R.string.update_yes, (d, w) ->
                        TvApkDownloader.start(act, REPO, assetId, url, name))
                .setNegativeButton(R.string.update_no, null)
                .show();
    }

    private static String currentVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName == null ? "0" : pi.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    static int compare(String remote, String local) {
        try {
            String[] r = remote.split("\\.");
            String[] l = local.split("\\.");
            int len = Math.max(r.length, l.length);
            for (int i = 0; i < len; i++) {
                int rv = i < r.length ? Integer.parseInt(r[i].replaceAll("\\D", "")) : 0;
                int lv = i < l.length ? Integer.parseInt(l[i].replaceAll("\\D", "")) : 0;
                if (rv != lv) return Integer.compare(rv, lv);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }
}
