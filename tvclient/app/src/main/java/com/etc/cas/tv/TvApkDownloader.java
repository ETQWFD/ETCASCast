package com.etc.cas.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TvApkDownloader {

    private static final String[] MIRRORS = {
            "https://ghfast.top/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/"
    };

    public static void cleanup(android.content.Context ctx) {
        try {
            File dir = new File(ctx.getExternalFilesDir(null), "updates");
            if (!dir.exists()) return;
            File[] fs = dir.listFiles();
            if (fs == null) return;
            for (File f : fs) {
                String n = f.getName();
                if (n.endsWith(".apk") || n.endsWith(".part")) f.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public static void start(final Activity act, final String repo, final long assetId,
                             final String browserUrl, final String fileName) {
        final Handler ui = new Handler(Looper.getMainLooper());

        final List<String[]> candidates = new ArrayList<>();
        if (assetId > 0) {
            candidates.add(new String[]{
                    "https://api.github.com/repos/" + repo + "/releases/assets/" + assetId, "api"});
        }
        if (browserUrl != null && !browserUrl.isEmpty()) {
            for (String m : MIRRORS) candidates.add(new String[]{m + browserUrl, "mirror"});
            candidates.add(new String[]{browserUrl, "direct"});
        }

        View v = LayoutInflater.from(act).inflate(R.layout.dialog_tv_update, null);
        final AlertDialog dialog = new AlertDialog.Builder(act)
                .setView(v)
                .setCancelable(false)
                .create();

        final TextView tvSub = v.findViewById(R.id.udl_subtitle);
        final TextView tvPercent = v.findViewById(R.id.udl_percent);
        final TextView tvMeter = v.findViewById(R.id.udl_meter);
        final TextView tvStatus = v.findViewById(R.id.udl_status);
        final ProgressBar bar = v.findViewById(R.id.udl_progress);
        final View btnRetry = v.findViewById(R.id.udl_btn_retry);
        final View btnCancel = v.findViewById(R.id.udl_btn_cancel);

        tvSub.setText(fileName);
        bar.setProgress(0);

        final Engine engine = new Engine(act, fileName, candidates, new Engine.Cb() {
            @Override
            public void connecting(int idx, int total) {
                ui.post(() -> {
                    btnRetry.setVisibility(View.GONE);
                    btnCancel.setEnabled(true);
                    tvStatus.setText(total > 1
                            ? act.getString(R.string.udl_line, idx, total)
                            : act.getString(R.string.udl_connecting));
                });
            }

            @Override
            public void lengthKnown(final long totalBytes) {
            }

            @Override
            public void progress(final long done, final long totalBytes, final double speed) {
                ui.post(() -> {
                    String speedMb = String.format(Locale.US, "%.1f", speed);
                    if (totalBytes > 0) {
                        int p = (int) (done * 100 / totalBytes);
                        bar.setProgress(p);
                        tvPercent.setText(p + "%");
                        tvMeter.setText(act.getString(R.string.udl_meter,
                                mb(done), mb(totalBytes), speedMb));
                    } else {
                        tvPercent.setText("…");
                        tvMeter.setText(act.getString(R.string.udl_meter_unknown,
                                mb(done), speedMb));
                    }
                });
            }

            @Override
            public void verifying() {
                ui.post(() -> tvStatus.setText(R.string.udl_verify));
            }

            @Override
            public void installing(final File apk) {
                ui.post(() -> {
                    tvStatus.setText(R.string.udl_install);
                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    openInstaller(act, apk);
                });
            }

            @Override
            public void failed() {
                ui.post(() -> {
                    bar.setProgress(0);
                    tvPercent.setText("!");
                    tvStatus.setText(R.string.udl_failed);
                    btnRetry.setVisibility(View.VISIBLE);
                    btnRetry.requestFocus();
                });
            }
        });

        btnCancel.setOnClickListener(x -> {
            engine.cancel();
            dialog.dismiss();
        });
        btnRetry.setOnClickListener(x -> {
            btnRetry.setVisibility(View.GONE);
            engine.reset();
            engine.start();
        });

        dialog.setOnDismissListener(d -> engine.cancel());
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        btnCancel.requestFocus();
        engine.start();
    }

    private static String mb(long bytes) {
        double m = bytes / 1048576.0;
        if (m >= 1) return String.format(Locale.US, "%.1f MB", m);
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
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

    private static final class Engine {
        interface Cb {
            void connecting(int idx, int total);

            void lengthKnown(long totalBytes);

            void progress(long done, long totalBytes, double speed);

            void verifying();

            void installing(File apk);

            void failed();
        }

        private final Activity act;
        private final String fileName;
        private final List<String[]> candidates;
        private final Cb cb;
        private volatile boolean cancelled;
        private Thread worker;

        Engine(Activity act, String fileName, List<String[]> candidates, Cb cb) {
            this.act = act;
            this.fileName = fileName;
            this.candidates = candidates;
            this.cb = cb;
        }

        void reset() {
            cancelled = false;
        }

        void cancel() {
            cancelled = true;
            if (worker != null) worker.interrupt();
        }

        void start() {
            worker = new Thread(this::run, "etcas-tv-update-dl");
            worker.setDaemon(true);
            worker.start();
        }

        private void run() {
            File dir = new File(act.getExternalFilesDir(null), "updates");
            if (!dir.exists()) dir.mkdirs();
            final File part = new File(dir, fileName + ".part");
            final File target = new File(dir, fileName);

            int n = candidates.size();
            for (int i = 0; i < n; i++) {
                if (cancelled) return;
                String[] c = candidates.get(i);
                cb.connecting(i + 1, n);
                HttpURLConnection conn = null;
                InputStream in = null;
                OutputStream out = null;
                try {
                    conn = (HttpURLConnection) new URL(c[0]).openConnection();
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "ETCASCastTV");
                    if ("api".equals(c[1])) {
                        conn.setRequestProperty("Accept", "application/octet-stream");
                    }
                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code != 200) {
                        quietDisconnect(conn);
                        continue;
                    }
                    final long total = conn.getContentLengthLong();
                    cb.lengthKnown(total);
                    in = conn.getInputStream();
                    out = new FileOutputStream(part, false);
                    byte[] buf = new byte[65536];
                    long done = 0;
                    long lastUi = 0L;
                    long lastBytes = 0L;
                    long lastT = System.currentTimeMillis();
                    int read;
                    while (!cancelled && (read = in.read(buf)) > 0) {
                        out.write(buf, 0, read);
                        done += read;
                        long now = System.currentTimeMillis();
                        if (now - lastUi >= 200) {
                            double dt = (now - lastT) / 1000.0;
                            double speed = dt > 0 ? (done - lastBytes) / dt / 1048576.0 : 0;
                            cb.progress(done, total, speed);
                            lastUi = now;
                            lastT = now;
                            lastBytes = done;
                        }
                    }
                    out.flush();
                    out.close();
                    out = null;
                    in.close();
                    in = null;
                    quietDisconnect(conn);
                    conn = null;
                    if (cancelled) {
                        part.delete();
                        return;
                    }
                    cb.verifying();
                    if (!validApk(part, total)) {
                        part.delete();
                        continue;
                    }
                    if (target.exists()) target.delete();
                    if (!part.renameTo(target)) {
                        copy(part, target);
                        part.delete();
                    }
                    if (!cancelled) cb.installing(target);
                    return;
                } catch (Exception e) {
                    try {
                        if (out != null) out.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        if (in != null) in.close();
                    } catch (Exception ignored) {
                    }
                    quietDisconnect(conn);
                    part.delete();
                }
            }
            if (!cancelled) cb.failed();
        }

        private static void quietDisconnect(HttpURLConnection conn) {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }

        private static boolean validApk(File f, long expected) {
            if (f == null || !f.exists()) return false;
            long len = f.length();
            if (len < 100 * 1024L) return false;
            if (expected > 0 && len != expected) return false;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                return fis.read() == 0x50 && fis.read() == 0x4B
                        && fis.read() == 0x03 && fis.read() == 0x04;
            } catch (Exception e) {
                return false;
            }
        }

        private static void copy(File src, File dst) throws Exception {
            try (InputStream in = new java.io.FileInputStream(src);
                 OutputStream o = new FileOutputStream(dst)) {
                byte[] b = new byte[65536];
                int r;
                while ((r = in.read(b)) > 0) o.write(b, 0, r);
            }
        }
    }
}
