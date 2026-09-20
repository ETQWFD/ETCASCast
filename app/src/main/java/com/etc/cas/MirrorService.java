package com.etc.cas;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import com.etc.cas.cast.LocalFileServer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class MirrorService extends Service {

    public static final String ACTION_START = "com.etc.cas.START_MIRROR";
    private static final long FRAME_INTERVAL_MS = 66L;

    private MediaProjection projection;
    private VirtualDisplay vd;
    private ImageReader imageReader;
    private HandlerThread encodeThread;
    private Handler encodeHandler;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;
    private long lastFrameTs;

    @Override
    public void onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("mirror", getString(R.string.mirror_title),
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "mirror");
        } else {
            builder = new Notification.Builder(this);
        }
        Notification notification = builder
                .setContentTitle(getString(R.string.mirror_title))
                .setContentText(getString(R.string.mirror_running))
                .setSmallIcon(R.drawable.ic_cast)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
        acquireLocks();
        int code = intent.getIntExtra("code", 0);
        Intent data = intent.getParcelableExtra("data");
        startCapture(code, data);
        return START_NOT_STICKY;
    }

    private void startCapture(int code, Intent data) {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) {
                stopSelf();
                return;
            }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopSelf();
                }
            }, new Handler(getMainLooper()));
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            int dpi = getResources().getDisplayMetrics().densityDpi;
            encodeThread = new HandlerThread("etcas-mirror");
            encodeThread.start();
            encodeHandler = new Handler(encodeThread.getLooper());
            int capW = Math.min(w, 1280);
            int capH = (int) ((long) capW * h / Math.max(w, 1));
            imageReader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2);
            vd = projection.createVirtualDisplay("ETCASMirror", capW, capH, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, encodeHandler);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;
                    long now = System.currentTimeMillis();
                    if (now - lastFrameTs < FRAME_INTERVAL_MS) return;
                    lastFrameTs = now;
                    Bitmap frame = toBitmap(image, capW, capH);
                    if (frame == null) return;
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    frame.compress(Bitmap.CompressFormat.JPEG, 55, bos);
                    frame.recycle();
                    byte[] jpeg = bos.toByteArray();
                    if (jpeg.length > 0) LocalFileServer.setFrame(jpeg);
                } catch (Exception ignored) {
                } finally {
                    if (image != null) image.close();
                }
            }, encodeHandler);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void acquireLocks() {
        if (wifiLock == null) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "etcas:mirror-wifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        }
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "etcas:mirror-wake");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(60 * 60 * 1000L);
        }
    }

    private void releaseLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        wifiLock = null;
        wakeLock = null;
    }

    private Bitmap toBitmap(Image image, int w, int h) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length == 0) return null;
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * w;
        Bitmap full = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buffer);
        Bitmap frame = Bitmap.createBitmap(full, 0, 0, w, h);
        full.recycle();
        return frame;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        LocalFileServer.setFrame(null);
        releaseLocks();
        try {
            if (vd != null) vd.release();
        } catch (Exception ignored) {
        }
        try {
            if (imageReader != null) imageReader.close();
        } catch (Exception ignored) {
        }
        try {
            if (projection != null) projection.stop();
        } catch (Exception ignored) {
        }
        try {
            if (encodeThread != null) encodeThread.quitSafely();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
