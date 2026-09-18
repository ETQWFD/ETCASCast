package com.etc.cas;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.SurfaceHolder;

import com.etc.cas.cast.LocalFileServer;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class MirrorService extends Service {

    public static final String ACTION_START = "com.etc.cas.START_MIRROR";
    public static volatile WeakReference<SurfaceHolder> previewHolder;

    private MediaProjection projection;
    private VirtualDisplay vd;
    private ImageReader imageReader;
    private HandlerThread encodeThread;
    private Handler encodeHandler;

    @Override
    public void onCreate() {
        NotificationChannel channel = new NotificationChannel("mirror", getString(R.string.mirror_title),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification notification = new Notification.Builder(this, "mirror")
                .setContentTitle(getString(R.string.mirror_title))
                .setContentText(getString(R.string.mirror_running))
                .setSmallIcon(R.drawable.ic_cast)
                .build();
        startForeground(1, notification);
        int code = intent.getIntExtra("code", 0);
        Intent data = intent.getParcelableExtra("data");
        startCapture(code, data);
        return START_NOT_STICKY;
    }

    private void startCapture(int code, Intent data) {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            int dpi = getResources().getDisplayMetrics().densityDpi;
            encodeThread = new HandlerThread("etcas-mirror");
            encodeThread.start();
            encodeHandler = new Handler(encodeThread.getLooper());
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            vd = projection.createVirtualDisplay("ETCASMirror", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, encodeHandler);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;
                    Bitmap frame = toBitmap(image, w, h);
                    if (frame == null) return;
                    SurfaceHolder holder = previewHolder == null ? null : previewHolder.get();
                    if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                        try {
                            Canvas canvas = holder.lockCanvas();
                            if (canvas != null) {
                                canvas.drawBitmap(frame, 0, 0, null);
                                holder.unlockCanvasAndPost(canvas);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    frame.compress(Bitmap.CompressFormat.JPEG, 72, bos);
                    frame.recycle();
                    LocalFileServer.setFrame(bos.toByteArray());
                } catch (Exception ignored) {
                } finally {
                    if (image != null) image.close();
                }
            }, encodeHandler);
        } catch (Exception e) {
            stopSelf();
        }
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
