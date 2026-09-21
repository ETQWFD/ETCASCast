package com.etc.cas.cast;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.etc.cas.R;
import com.etc.cas.discovery.CastDevice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PairGate {

    public interface Ready {
        void onReady(CastDevice device);
    }

    private static final Set<String> TRUSTED = Collections.synchronizedSet(new HashSet<>());

    public static void request(final Activity act, final CastDevice d, final Ready ready) {
        if (d == null || !d.etcas) {
            if (ready != null) ready.onReady(d);
            return;
        }
        if (TRUSTED.contains(identity(d))) {
            if (ready != null) ready.onReady(d);
            return;
        }
        askCode(act, d, ready);
    }

    private static String identity(CastDevice d) {
        if (d.udn != null && !d.udn.isEmpty()) return "u:" + d.udn;
        return "h:" + d.ip + ":" + d.port;
    }

    private static void askCode(final Activity act, final CastDevice d, final Ready ready) {
        final EditText input = new EditText(act);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.pair_code_hint);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(6)});
        new AlertDialog.Builder(act)
                .setTitle(R.string.pair_title)
                .setMessage(d.name + "\n" + d.ip + ":" + d.port)
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(R.string.confirm, (dlg, w) -> {
                    String code = input.getText().toString().trim();
                    if (code.length() != 6) {
                        Toast.makeText(act, R.string.pair_code_hint, Toast.LENGTH_SHORT).show();
                        askCode(act, d, ready);
                        return;
                    }
                    verify(act, d, code, ready);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void verify(final Activity act, final CastDevice d, final String code,
                               final Ready ready) {
        Toast.makeText(act, R.string.pairing, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final boolean ok = postPair(d, code);
            act.runOnUiThread(() -> {
                if (ok) {
                    d.key = code;
                    TRUSTED.add(identity(d));
                    Toast.makeText(act, R.string.pair_ok, Toast.LENGTH_SHORT).show();
                    if (ready != null) ready.onReady(d);
                } else {
                    Toast.makeText(act, R.string.pair_fail, Toast.LENGTH_LONG).show();
                    askCode(act, d, ready);
                }
            });
        }, "etcas-pair").start();
    }

    private static boolean postPair(CastDevice d, String code) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(d.baseUrl() + "/etcas/pair");
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Connection", "close");
            conn.setDoOutput(true);
            byte[] body = ("key=" + URLEncoder.encode(code, "UTF-8")).getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
            int sc = conn.getResponseCode();
            if (sc != 200) return false;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8").contains("\"ok\":true");
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
