package com.etc.cas.tv;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TvApkDownloader {

    public static void cleanup(android.content.Context ctx) {
        try {
            File dir = new File(ctx.getExternalFilesDir(null), "updates");
            if (!dir.exists()) return;
            File[] fs = dir.listFiles();
            if (fs == null) return;
            for (File f : fs) {
                if (f.getName().endsWith(".apk")) f.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public static void install(final Activity act, final String url, final String fileName) {
        final ProgressDialog pd = new ProgressDialog(act);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setProgress(0);
        pd.setCancelable(false);
        pd.setMessage(act.getString(R.string.update_downloading));
        pd.setButton(ProgressDialog.BUTTON_NEGATIVE, act.getString(R.string.update_no),
                (d, w) -> d.dismiss());
        pd.show();

        new Thread(() -> {
            File apk = null;
            try {
                File dir = new File(act.getExternalFilesDir(null), "updates");
                if (!dir.exists()) dir.mkdirs();
                apk = new File(dir, fileName);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "ETCASCastTV");
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                final int total = conn.getContentLength();
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buf = new byte[65536];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        final int p = (int) (done * 100 / total);
                        act.runOnUiThread(() -> {
                            try { pd.setProgress(p); } catch (Exception ignored) {}
                        });
                    }
                }
                out.flush();
                out.close();
                in.close();
                conn.disconnect();

                final File target = apk;
                act.runOnUiThread(() -> {
                    try { pd.dismiss(); } catch (Exception ignored) {}
                    openInstaller(act, target);
                });
            } catch (final Exception e) {
                final File target = apk;
                act.runOnUiThread(() -> {
                    try { pd.dismiss(); } catch (Exception ignored) {}
                    Toast.makeText(act, R.string.update_download_fail, Toast.LENGTH_LONG).show();
                    if (target != null) target.delete();
                });
            }
        }, "etcas-tv-update").start();
    }

    private static void openInstaller(Activity act, File apk) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(act, act.getPackageName() + ".fileprovider", apk);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(apk);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            act.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(act, R.string.update_install_fail, Toast.LENGTH_LONG).show();
        }
    }
}
