package com.etc.cas.tv.receiver;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

public final class CastState {

    public interface Listener {
        void onMediaChanged(String uri, String title);

        void onPlayStateChanged(boolean playing);

        void onVolumeChanged(int volume);
    }

    private static final CastState INSTANCE = new CastState();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile String uri;
    private volatile String title;
    private volatile boolean playing;
    private volatile int volume = 80;

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

    public void setMedia(final String newUri, final String newTitle) {
        this.uri = newUri;
        this.title = newTitle;
        main.post(() -> {
            for (Listener l : listeners) l.onMediaChanged(newUri, newTitle);
            if (newUri != null && !newUri.isEmpty()) setPlaying(true);
        });
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
}
