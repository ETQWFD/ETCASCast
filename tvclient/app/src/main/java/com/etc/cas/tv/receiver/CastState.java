package com.etc.cas.tv.receiver;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

public final class CastState {

    public interface Listener {
        void onMediaChanged(String uri, String title, int kind);

        void onPlayStateChanged(boolean playing);

        void onVolumeChanged(int volume);

        void onSpeedChanged(float speed);

        void onQualityChanged(int quality);

        void onPaired();

        void onCleared();
    }

    public static final int KIND_VIDEO = 0;
    public static final int KIND_IMAGE = 1;
    public static final int KIND_AUDIO = 2;

    private static final CastState INSTANCE = new CastState();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile String uri;
    private volatile String title;
    private volatile boolean playing;
    private volatile boolean paired;
    private volatile int volume = 80;
    private volatile float speed = 1.0f;
    private volatile int quality = 0;
    private volatile int mediaKind = KIND_VIDEO;
    private volatile long positionMs;
    private volatile long durationMs = -1L;

    private CastState() {
    }

    public static CastState get() {
        return INSTANCE;
    }

    public void register(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void unregister(Listener l) {
        listeners.remove(l);
    }

    public void setMedia(final String newUri, final String newTitle, final int kind) {
        this.uri = newUri;
        this.title = newTitle;
        this.mediaKind = kind;
        if (newUri != null && !newUri.isEmpty()) this.paired = true;
        main.post(() -> {
            for (Listener l : listeners) l.onMediaChanged(newUri, newTitle, kind);
            if (newUri != null && !newUri.isEmpty()) setPlaying(true);
        });
    }

    public int getKind() {
        return mediaKind;
    }

    public void notifyPaired() {
        this.paired = true;
        main.post(() -> {
            for (Listener l : listeners) l.onPaired();
        });
    }

    public void clear() {
        this.uri = null;
        this.title = null;
        this.playing = false;
        this.paired = false;
        this.positionMs = 0L;
        this.durationMs = -1L;
        main.post(() -> {
            for (Listener l : listeners) {
                l.onCleared();
                l.onPlayStateChanged(false);
            }
        });
    }

    public boolean isPaired() {
        return paired;
    }

    public void setPlaying(final boolean p) {
        this.playing = p;
        main.post(() -> {
            for (Listener l : listeners) l.onPlayStateChanged(p);
        });
    }

    public void setVolume(final int v) {
        this.volume = Math.max(0, Math.min(100, v));
        main.post(() -> {
            for (Listener l : listeners) l.onVolumeChanged(volume);
        });
    }

    public void setSpeed(final float s) {
        this.speed = Math.max(0.25f, Math.min(2.0f, s));
        main.post(() -> {
            for (Listener l : listeners) l.onSpeedChanged(speed);
        });
    }

    public float getSpeed() {
        return speed;
    }

    public void setQuality(final int q) {
        this.quality = Math.max(0, Math.min(3, q));
        main.post(() -> {
            for (Listener l : listeners) l.onQualityChanged(quality);
        });
    }

    public int getQuality() {
        return quality;
    }

    public String getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getVolume() {
        return volume;
    }

    public void setProgress(long position, long duration) {
        if (position >= 0) this.positionMs = position;
        if (duration >= 0) this.durationMs = duration;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
