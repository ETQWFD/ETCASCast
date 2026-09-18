package com.etc.cas.cast;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

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

public class LocalFileServer {

    private static final int BASE_PORT = 8388;
    private static volatile ServerSocket server;
    private static volatile Thread acceptThread;
    private static volatile boolean running;
    private static volatile Uri currentUri;
    private static volatile String currentMime;
    private static volatile byte[] mirrorFrame;

    public static synchronized String start(Context ctx, Uri uri, String mime) {
        currentUri = uri;
        currentMime = mime;
        if (server != null) return baseUrl(ctx);
        running = true;
        try {
            ServerSocket ss = null;
            for (int port = BASE_PORT; port < BASE_PORT + 20; port++) {
                try {
                    ss = new ServerSocket(port);
                    break;
                } catch (IOException ignored) {
                }
            }
            if (ss == null) return null;
            server = ss;
            acceptThread = new Thread(() -> acceptLoop(), "etcas-http");
            acceptThread.start();
        } catch (Exception e) {
            return null;
        }
        return baseUrl(ctx);
    }

    public static void setFrame(byte[] frame) {
        mirrorFrame = frame;
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

    public static String mirrorPageUrl(Context ctx) {
        return baseUrl(ctx) + "/mirrorpage";
    }

    public static synchronized void stop() {
        running = false;
        mirrorFrame = null;
        currentUri = null;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        acceptThread = null;
    }

    private static void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> handle(s), "etcas-http-conn");
                t.start();
            } catch (SocketTimeoutException ignored) {
            } catch (Exception e) {
                break;
            }
        }
    }

    private static void handle(Socket s) {
        try {
            s.setSoTimeout(20000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null) {
                s.close();
                return;
            }
            String[] parts = requestLine.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            String rangeHeader = null;

            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("range:")) {
                    rangeHeader = line.substring(6).trim();
                }
            }

            if (path.startsWith("/mirror")) {
                serveMirror(s, out);
            } else if (path.startsWith("/mirrorpage")) {
                serveMirrorPage(out);
            } else if (path.startsWith("/file")) {
                serveFile(s, out, rangeHeader);
            } else {
                out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void serveFile(Socket s, OutputStream out, String rangeHeader) throws IOException {
        Context ctx = AppHolder.get();
        Uri uri = currentUri;
        if (ctx == null || uri == null) {
            out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
            out.flush();
            return;
        }
        ContentResolver cr = ctx.getContentResolver();
        long total = querySize(cr, uri);
        String mime = currentMime != null ? currentMime : "application/octet-stream";

        InputStream src;
        if ("file".equals(uri.getScheme())) {
            src = new java.io.FileInputStream(uri.getPath());
        } else {
            src = cr.openInputStream(uri);
        }
        if (src == null) {
            out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
            out.flush();
            return;
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
            out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
            out.flush();
            src.close();
            return;
        }

        long skip = start;
        byte[] buf = new byte[65536];
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
        long len = total > 0 ? end - start + 1 : Long.MAX_VALUE;
        head.append("Content-Type: ").append(mime).append("\r\n");
        if (total > 0) head.append("Content-Length: ").append(len).append("\r\n");
        head.append("Accept-Ranges: bytes\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes());
        out.flush();

        long remaining = len;
        while (remaining > 0) {
            int n = src.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) break;
            out.write(buf, 0, n);
            remaining -= n;
        }
        out.flush();
        src.close();
    }

    private static void serveMirrorPage(OutputStream out) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>ETCAS Mirror</title></head>"
                + "<body style=\"margin:0;background:#000\">"
                + "<img src=\"/mirror\" style=\"width:100vw;height:100vh;object-fit:contain\">"
                + "</body></html>";
        byte[] data = html.getBytes("UTF-8");
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\nConnection: close\r\n\r\n").getBytes());
        out.write(data);
        out.flush();
    }

    private static void serveMirror(Socket s, OutputStream out) throws IOException {
        if (mirrorFrame == null) {
            out.write(("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
            out.flush();
            return;
        }
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: multipart/x-mixed-replace; boundary=etcasframe\r\n"
                + "Connection: close\r\n\r\n").getBytes());
        out.flush();
        int idle = 0;
        while (running) {
            byte[] frame = mirrorFrame;
            if (frame == null || frame.length == 0) {
                if (++idle > 100) break;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            idle = 0;
            StringBuilder head = new StringBuilder();
            head.append("--etcasframe\r\n");
            head.append("Content-Type: image/jpeg\r\n");
            head.append("Content-Length: ").append(frame.length).append("\r\n\r\n");
            out.write(head.toString().getBytes());
            out.write(frame);
            out.write("\r\n".getBytes());
            out.flush();
            try {
                Thread.sleep(40);
            } catch (InterruptedException ignored) {
            }
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
