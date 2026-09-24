package com.etc.cas.cast;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class LocalFileServer {

    private static final int BASE_PORT = 8388;
    private static final int BUF_SIZE = 262144;
    private static final int KEEP_ALIVE_TIMEOUT_MS = 15000;
    private static final int MAX_REQUESTS_PER_CONN = 64;
    private static volatile ServerSocket server;
    private static volatile Thread acceptThread;
    private static volatile ThreadPoolExecutor pool;
    private static volatile boolean running;
    private static volatile Uri currentUri;
    private static volatile String currentMime;
    private static volatile byte[] mirrorFrame;
    private static volatile long mirrorSeq;
    private static volatile WifiManager.WifiLock wifiLock;

    public static synchronized String start(Context ctx, Uri uri, String mime) {
        currentMime = mime;
        Uri serving = uri;
        if (mime != null && mime.startsWith("image/")) {
            serving = prepareImage(ctx, uri);
            currentMime = "image/jpeg";
        }
        currentUri = serving;
        if (server != null) return baseUrl(ctx);
        running = true;
        acquireWifiLock(ctx);
        try {
            ServerSocket ss = null;
            for (int port = BASE_PORT; port < BASE_PORT + 20; port++) {
                try {
                    ss = new ServerSocket(port);
                    break;
                } catch (IOException ignored) {
                }
            }
            if (ss == null) {
                releaseWifiLock();
                return null;
            }
            server = ss;
            pool = new ThreadPoolExecutor(2, 16, 30, TimeUnit.SECONDS,
                    new SynchronousQueue<>(), r -> {
                Thread t = new Thread(r, "etcas-http-conn");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());
            acceptThread = new Thread(() -> acceptLoop(), "etcas-http");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (Exception e) {
            releaseWifiLock();
            return null;
        }
        return baseUrl(ctx);
    }

    public static void setFrame(byte[] frame) {
        mirrorFrame = frame;
        mirrorSeq++;
    }

    private static Uri prepareImage(Context ctx, Uri uri) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = openStream(ctx, uri)) {
                BitmapFactory.decodeStream(is, null, opts);
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return uri;
            int sample = 1;
            while (Math.max(opts.outWidth / sample, opts.outHeight / sample) > 2560) sample *= 2;
            Bitmap bmp;
            try (InputStream is = openStream(ctx, uri)) {
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                bmp = BitmapFactory.decodeStream(is, null, opts);
            }
            if (bmp == null) return uri;
            File dir = new File(ctx.getCacheDir(), "etcas_img");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "cast_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos);
            }
            bmp.recycle();
            return Uri.fromFile(out);
        } catch (Exception e) {
            return uri;
        }
    }

    private static InputStream openStream(Context ctx, Uri uri) throws IOException {
        if ("file".equals(uri.getScheme())) return new java.io.FileInputStream(uri.getPath());
        return ctx.getContentResolver().openInputStream(uri);
    }

    public static String baseUrl(Context ctx) {
        String ip = localIp();
        int port = server != null ? server.getLocalPort() : BASE_PORT;
        return "http://" + ip + ":" + port;
    }

    public static String fileUrl(Context ctx) {
        return baseUrl(ctx) + "/file";
    }

    public static String mirrorUrl(Context ctx) {
        return baseUrl(ctx) + "/mirror";
    }

    public static String frameUrl(Context ctx) {
        return baseUrl(ctx) + "/frame";
    }

    public static String mirrorPageUrl(Context ctx) {
        return baseUrl(ctx) + "/mirrorpage";
    }

    private static void acquireWifiLock(Context ctx) {
        try {
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) ctx.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "etcas:cast-wifi");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void releaseWifiLock() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
        wifiLock = null;
    }

    public static synchronized void stop() {
        running = false;
        mirrorFrame = null;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        acceptThread = null;
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        releaseWifiLock();
    }

    private static void acceptLoop() {
        while (running) {
            try {
                final Socket s = server.accept();
                ThreadPoolExecutor p = pool;
                if (p != null) p.execute(() -> handle(s));
                else {
                    try { s.close(); } catch (Exception ignored) {}
                }
            } catch (SocketTimeoutException ignored) {
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    private static String connHeader(boolean keep) {
        return keep
                ? "Connection: keep-alive\r\nKeep-Alive: timeout=15\r\n"
                : "Connection: close\r\n";
    }

    private static void handle(Socket s) {
        boolean reusable = false;
        try {
            s.setSoTimeout(KEEP_ALIVE_TIMEOUT_MS);
            try {
                s.setSendBufferSize(BUF_SIZE);
                s.setReceiveBufferSize(BUF_SIZE);
            } catch (Exception ignored) {
            }
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            for (int reqNo = 0; reqNo < MAX_REQUESTS_PER_CONN; reqNo++) {
                String requestLine = readLine(in);
                if (requestLine == null) break;
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) break;
                boolean http11 = requestLine.startsWith("GET") && requestLine.endsWith("HTTP/1.1");
                String path = parts[1];
                String rangeHeader = null;
                boolean clientKeepAlive = http11;

                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    String low = line.toLowerCase();
                    if (low.startsWith("range:")) {
                        rangeHeader = line.substring(6).trim();
                    } else if (low.startsWith("connection:")) {
                        String v = line.substring(11).trim().toLowerCase();
                        clientKeepAlive = !v.contains("close");
                        if (v.contains("keep-alive")) clientKeepAlive = true;
                    }
                }

                String pathOnly = path;
                int q = pathOnly.indexOf('?');
                if (q >= 0) pathOnly = pathOnly.substring(0, q);

                reusable = false;
                if (pathOnly.startsWith("/frame")) {
                    serveFrame(out, clientKeepAlive);
                    reusable = clientKeepAlive;
                } else if (pathOnly.startsWith("/mirrorpage")) {
                    serveMirrorPage(out, clientKeepAlive);
                    reusable = clientKeepAlive;
                } else if (pathOnly.startsWith("/mirror")) {
                    serveMirror(out);
                    reusable = false;
                } else if (pathOnly.startsWith("/file")) {
                    reusable = serveFile(in, out, rangeHeader, clientKeepAlive);
                } else {
                    out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n"
                            + connHeader(clientKeepAlive) + "\r\n").getBytes());
                    out.flush();
                    reusable = clientKeepAlive;
                }
                if (!reusable) break;
            }
        } catch (SocketTimeoutException ignored) {
        } catch (Exception ignored) {
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean serveFile(InputStream ignoredIn, OutputStream out,
                                     String rangeHeader, boolean clientKeepAlive) throws IOException {
        Context ctx = AppHolder.get();
        Uri uri = currentUri;
        if (ctx == null || uri == null) {
            out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n"
                    + connHeader(clientKeepAlive) + "\r\n").getBytes());
            out.flush();
            return clientKeepAlive;
        }
        ContentResolver cr = ctx.getContentResolver();
        long total = querySize(cr, uri);
        String mime = currentMime != null ? currentMime : "application/octet-stream";

        boolean keep = clientKeepAlive && total > 0;

        InputStream src;
        if ("file".equals(uri.getScheme())) {
            src = new java.io.FileInputStream(uri.getPath());
        } else {
            src = cr.openInputStream(uri);
        }
        if (src == null) {
            out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n"
                    + connHeader(clientKeepAlive) + "\r\n").getBytes());
            out.flush();
            return clientKeepAlive;
        }

        long start = 0;
        long end = total > 0 ? total - 1 : Long.MAX_VALUE;
        boolean partial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String r = rangeHeader.substring(6).replace(" ", "");
            int dash = r.indexOf('-');
            try {
                long rs = Long.parseLong(r.substring(0, dash));
                start = rs;
                if (dash + 1 < r.length()) {
                    long re = Long.parseLong(r.substring(dash + 1));
                    end = re;
                }
                partial = true;
            } catch (Exception ignored) {
            }
        }
        if (total > 0 && end > total - 1) end = total - 1;
        if (start < 0) start = 0;
        if (partial && total > 0 && start >= total) {
            out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Length: 0\r\n"
                    + connHeader(keep) + "\r\n").getBytes());
            out.flush();
            src.close();
            return keep;
        }

        long skip = start;
        byte[] buf = new byte[BUF_SIZE];
        while (skip > 0) {
            long n = src.skip(skip);
            if (n <= 0) {
                if (src.read() == -1) break;
                n = 1;
            }
            skip -= n;
        }

        StringBuilder head = new StringBuilder();
        if (partial) {
            head.append("HTTP/1.1 206 Partial Content\r\n");
            head.append("Content-Range: bytes ").append(start).append("-").append(end).append("/")
                    .append(total > 0 ? total : "*").append("\r\n");
        } else {
            head.append("HTTP/1.1 200 OK\r\n");
        }
        head.append("Content-Type: ").append(mime).append("\r\n");
        if (total > 0) head.append("Content-Length: ").append(end - start + 1).append("\r\n");
        head.append("Accept-Ranges: bytes\r\n");
        head.append(connHeader(keep)).append("\r\n");
        out.write(head.toString().getBytes());
        out.flush();

        long remaining = total > 0 ? end - start + 1 : Long.MAX_VALUE;
        while (remaining > 0) {
            int n = src.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) break;
            out.write(buf, 0, n);
            remaining -= n;
        }
        out.flush();
        src.close();
        return keep;
    }

    private static void serveMirrorPage(OutputStream out, boolean keep) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>ETCAS Mirror</title></head>"
                + "<body style=\"margin:0;background:#000\">"
                + "<img src=\"/mirror\" style=\"width:100vw;height:100vh;object-fit:contain\">"
                + "</body></html>";
        byte[] data = html.getBytes("UTF-8");
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + connHeader(keep) + "\r\n").getBytes());
        out.write(data);
        out.flush();
    }

    private static void serveFrame(OutputStream out, boolean keep) throws IOException {
        byte[] frame = mirrorFrame;
        if (frame == null || frame.length == 0) {
            out.write(("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n"
                    + "Cache-Control: no-store\r\n" + connHeader(keep) + "\r\n").getBytes());
            out.flush();
            return;
        }
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: image/jpeg\r\n"
                + "Content-Length: " + frame.length + "\r\n"
                + "Cache-Control: no-store\r\n" + connHeader(keep) + "\r\n").getBytes());
        out.write(frame);
        out.flush();
    }

    private static void serveMirror(OutputStream out) throws IOException {
        if (mirrorFrame == null) {
            out.write(("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
            out.flush();
            return;
        }
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: multipart/x-mixed-replace; boundary=etcasframe\r\n"
                + "Cache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes());
        out.flush();
        long lastSeq = mirrorSeq;
        int idle = 0;
        while (running) {
            long seq = mirrorSeq;
            byte[] frame = mirrorFrame;
            if (seq == lastSeq || frame == null || frame.length == 0) {
                if (++idle > 1000) break;
                try {
                    Thread.sleep(12);
                } catch (InterruptedException ignored) {
                    break;
                }
                continue;
            }
            idle = 0;
            lastSeq = seq;
            StringBuilder h = new StringBuilder();
            h.append("--etcasframe\r\n");
            h.append("Content-Type: image/jpeg\r\n");
            h.append("Content-Length: ").append(frame.length).append("\r\n\r\n");
            out.write(h.toString().getBytes());
            out.write(frame);
            out.write("\r\n".getBytes());
            out.flush();
        }
    }

    private static long querySize(ContentResolver cr, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            try {
                return new java.io.File(uri.getPath()).length();
            } catch (Exception ignored) {
            }
            return -1;
        }
        try (Cursor c = cr.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String localIp() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String nif = ni.getName().toLowerCase();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String ip = a.getHostAddress();
                        if (fallback == null) fallback = ip;
                        if (nif.startsWith("wlan") || nif.startsWith("wifi") || nif.startsWith("ap")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback != null ? fallback : "127.0.0.1";
    }

    public static final class AppHolder {
        private static Context context;

        public static void init(Context c) {
            context = c.getApplicationContext();
        }

        public static Context get() {
            return context;
        }
    }
}
