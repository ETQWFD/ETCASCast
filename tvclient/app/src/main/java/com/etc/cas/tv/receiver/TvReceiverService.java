package com.etc.cas.tv.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;

import com.etc.cas.tv.R;
import com.etc.cas.tv.TvMainActivity;

import java.security.SecureRandom;

public class TvReceiverService extends Service {

    public static final String ACTION_START = "com.etc.cas.tv.START_RECEIVER";

    public static final class Info {
        public String ip;
        public int port;
        public String key;
        public String name;
        public String model;
        public String androidVersion;
        public String udn;
        public String qrPayload;
    }

    private static volatile Info INFO;
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UpnpServer upnpServer;
    private SsdpServer ssdpServer;
    private WifiManager.MulticastLock multicastLock;

    public static Info info() {
        return INFO;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel("etcas_tv",
                    getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(10, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(10, n);
        }
        startServers();
        return START_STICKY;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("etcas_tv", Context.MODE_PRIVATE);
    }

    private String loadKey() {
        String saved = prefs().getString("pair_key", null);
        if (saved != null && saved.matches("\\d{6}")) {
            return saved;
        }
        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        prefs().edit().putString("pair_key", code).apply();
        return code;
    }

    private String loadUdn() {
        String u = prefs().getString("udn", null);
        if (u == null) {
            byte[] b = new byte[8];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder("uuid:etcas-");
            for (byte x : b) sb.append(HEX[(x >> 4) & 0xF]).append(HEX[x & 0xF]);
            u = sb.toString();
            prefs().edit().putString("udn", u).apply();
        }
        return u;
    }

    private void startServers() {
        if (upnpServer != null) {
            refreshInfo();
            return;
        }
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String androidVersion = "Android " + Build.VERSION.RELEASE;
        String key = loadKey();
        String udn = loadUdn();
        String name = "ETCAS投屏客户端 (" + Build.MODEL + ")";

        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            multicastLock = wm.createMulticastLock("etcas-tv-ssdp");
        } catch (Exception ignored) {
        }

        upnpServer = new UpnpServer(name, udn, key, model, androidVersion);
        int port = upnpServer.start();
        if (port < 0) return;
        ssdpServer = new SsdpServer(port, udn, name, multicastLock);
        ssdpServer.start();

        Info info = new Info();
        info.port = port;
        info.key = key;
        info.udn = udn;
        info.name = name;
        info.model = model;
        info.androidVersion = androidVersion;
        INFO = info;
        refreshInfo();
    }

    private void refreshInfo() {
        Info i = INFO;
        if (i == null) return;
        i.ip = com.etc.cas.tv.util.NetUtil.localIpv4();
        i.qrPayload = "etcas://cast?ip=" + i.ip + "&port=" + i.port + "&k=" + i.key
                + "&n=" + android.net.Uri.encode(i.name);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, TvMainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlag = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlag);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, "etcas_tv") : new Notification.Builder(this);
        return b.setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_cast_tv)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        if (ssdpServer != null) ssdpServer.stop();
        if (upnpServer != null) upnpServer.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
