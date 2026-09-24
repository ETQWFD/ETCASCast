package com.etc.cas.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.etc.cas.tv.receiver.CastState;
import com.etc.cas.tv.receiver.TvReceiverService;
import com.etc.cas.tv.util.QrUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TvMainActivity extends AppCompatActivity {

    private static final int MODE_INFO = 0;
    private static final int MODE_LOADING = 1;
    private static final int MODE_VIDEO = 2;
    private static final int MODE_MIRROR = 3;
    private static final int MODE_IMAGE = 4;

    private PlayerView playerView;
    private ExoPlayer player;
    private View infoPanel;
    private View loadingRoot;
    private TextView loadingText;
    private ImageView mirrorImage;
    private ImageView imageView;
    private ImageView qrImage;
    private TextView tvIp;
    private TextView tvKey;
    private TextView tvDevice;
    private TextView tvStatus;

    private int mode = MODE_INFO;
    private boolean updateChecked;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int loadIndex;
    private String[] loadLines;
    private volatile boolean mirroring;
    private Thread mirrorThread;
    private volatile boolean imageLoading;
    private int imageReqSeq;
    private int imageRetry;

    private final Runnable loadTicker = new Runnable() {
        @Override
        public void run() {
            if (mode == MODE_LOADING && loadLines != null && loadLines.length > 0) {
                loadingText.setText(loadLines[loadIndex % loadLines.length]);
                loadIndex++;
                handler.postDelayed(this, 2000);
            }
        }
    };

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (mode == MODE_VIDEO && player != null) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                CastState.get().setProgress(pos, dur);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final CastState.Listener stateListener = new CastState.Listener() {
        @Override
        public void onMediaChanged(String uri, String title, int kind) {
            if (uri == null || uri.isEmpty()) return;
            if (uri.startsWith("etcas://mirror")) {
                enterMirror(uri);
            } else if (kind == CastState.KIND_IMAGE) {
                enterImage(uri, title);
            } else {
                enterVideo(uri, title);
            }
        }

        @Override
        public void onPlayStateChanged(boolean playing) {
            if (mode != MODE_VIDEO || player == null) return;
            if (playing) player.play();
            else player.pause();
        }

        @Override
        public void onVolumeChanged(int volume) {
            if (player != null) player.setVolume(volume / 100f);
        }

        @Override
        public void onSpeedChanged(float speed) {
            if (player != null) {
                player.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(speed, 1f));
            }
        }

        @Override
        public void onQualityChanged(int quality) {
            applyQuality(quality);
        }

        @Override
        public void onPaired() {
            enterLoading();
        }

        @Override
        public void onCleared() {
            enterInfo();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_tv_main);

        playerView = findViewById(R.id.player_view);
        infoPanel = findViewById(R.id.info_panel);
        loadingRoot = findViewById(R.id.loading_root);
        loadingText = findViewById(R.id.loading_text);
        mirrorImage = findViewById(R.id.mirror_image);
        qrImage = findViewById(R.id.img_qr);
        tvIp = findViewById(R.id.tv_ip);
        tvKey = findViewById(R.id.tv_key);
        tvDevice = findViewById(R.id.tv_device);
        tvStatus = findViewById(R.id.tv_status);

        loadLines = new String[]{
                getString(R.string.load_line_1),
                getString(R.string.load_line_2),
                getString(R.string.load_line_3),
                getString(R.string.load_line_4)
        };

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1500, 30000, 300, 1000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        player = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (mode != MODE_VIDEO) return;
                if (state == Player.STATE_BUFFERING) tvStatus.setText(R.string.main_loading);
                else if (state == Player.STATE_READY && player.getPlayWhenReady())
                    tvStatus.setText(R.string.main_casting);
            }
        });

        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (mode != MODE_INFO) {
                            CastState.get().clear();
                            enterInfo();
                        } else {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        handler.post(progressTicker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CastState.get().register(stateListener);
        bindInfo();
        String u = CastState.get().getUri();
        if (u != null && !u.isEmpty()) {
            if (u.startsWith("etcas://mirror")) {
                enterMirror(u);
            } else if (CastState.get().getKind() == CastState.KIND_IMAGE) {
                enterImage(u, CastState.get().getTitle());
            } else {
                enterVideo(u, CastState.get().getTitle());
            }
            if (player != null) {
                player.setPlaybackParameters(
                        new androidx.media3.common.PlaybackParameters(CastState.get().getSpeed(), 1f));
                applyQuality(CastState.get().getQuality());
            }
        } else if (CastState.get().isPaired()) {
            enterLoading();
        } else {
            enterInfo();
        }

        if (!updateChecked) {
            updateChecked = true;
            TvUpdateChecker.checkStart(this);
        }
    }

    @Override
    protected void onPause() {
        CastState.get().unregister(stateListener);
        super.onPause();
    }

    private void bindInfo() {
        TvReceiverService.Info info = TvReceiverService.info();
        if (info == null) return;
        if (info.ip == null || "0.0.0.0".equals(info.ip)) {
            info.ip = com.etc.cas.tv.util.NetUtil.localIpv4();
            info.qrPayload = "etcas://cast?ip=" + info.ip + "&port=" + info.port + "&k=" + info.key
                    + "&n=" + Uri.encode(info.name);
        }
        tvIp.setText(getString(R.string.main_ip) + "  " + info.ip + ":" + info.port);
        tvKey.setText(getString(R.string.main_key) + "  " + info.key);
        tvDevice.setText(info.model + "  ·  " + info.androidVersion);
        final String payload = info.qrPayload;
        new Thread(() -> {
            Bitmap bmp = QrUtil.create(payload, 480);
            if (bmp != null) runOnUiThread(() -> qrImage.setImageBitmap(bmp));
        }, "etcas-tv-qr").start();
    }

    private void enterInfo() {
        mode = MODE_INFO;
        handler.removeCallbacks(loadTicker);
        stopMirror();
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        loadingRoot.setVisibility(View.GONE);
        mirrorImage.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        infoPanel.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.main_waiting);
    }

    private void enterLoading() {
        mode = MODE_LOADING;
        stopMirror();
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        playerView.setVisibility(View.GONE);
        mirrorImage.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        infoPanel.setVisibility(View.GONE);
        loadingRoot.setVisibility(View.VISIBLE);
        loadIndex = 0;
        handler.removeCallbacks(loadTicker);
        loadingText.setText(loadLines[0]);
        loadIndex = 1;
        handler.postDelayed(loadTicker, 2000);
        tvStatus.setText(R.string.main_loading);
    }

    private void applyQuality(int q) {
        if (player == null) return;
        try {
            androidx.media3.common.TrackSelectionParameters.Builder b =
                    player.getTrackSelectionParameters().buildUpon();
            switch (q) {
                case 1:
                    player.setTrackSelectionParameters(b
                            .setMaxVideoSize(1920, 1080)
                            .setMaxVideoBitrate(8_000_000)
                            .build());
                    break;
                case 2:
                    player.setTrackSelectionParameters(b
                            .setMaxVideoSize(1280, 720)
                            .setMaxVideoBitrate(3_000_000)
                            .build());
                    break;
                case 3:
                    player.setTrackSelectionParameters(b
                            .setMaxVideoSize(854, 480)
                            .setMaxVideoBitrate(1_200_000)
                            .build());
                    break;
                default:
                    player.setTrackSelectionParameters(
                            player.getTrackSelectionParameters().buildUpon()
                                    .setForceLowestBitrate(false)
                                    .clearVideoSizeConstraints()
                                    .setMaxVideoBitrate(Integer.MAX_VALUE)
                                    .build());
            }
        } catch (Exception ignored) {
        }
    }

    private void enterVideo(String uri, String title) {
        if (isImageUri(uri)) {
            enterImage(uri, title);
            return;
        }
        mode = MODE_VIDEO;
        handler.removeCallbacks(loadTicker);
        stopMirror();
        mirrorImage.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        loadingRoot.setVisibility(View.GONE);
        infoPanel.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        if (player != null) {
            CastState.get().setProgress(0L, -1L);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)));
            player.prepare();
            player.play();
        }
        tvStatus.setText(getString(R.string.main_casting) + (title == null || title.isEmpty() ? "" : " · " + title));
    }

    private void enterImage(String uri, String title) {
        mode = MODE_IMAGE;
        handler.removeCallbacks(loadTicker);
        stopMirror();
        imageRetry = 0;
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        playerView.setVisibility(View.GONE);
        mirrorImage.setVisibility(View.GONE);
        loadingRoot.setVisibility(View.GONE);
        infoPanel.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.main_image_loading);
        loadImage(uri);
    }

    private void loadImage(final String uri) {
        final int seq = ++imageReqSeq;
        imageLoading = true;
        Thread t = new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(uri);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Connection", "close");
                int code = conn.getResponseCode();
                if (code != 200) throw new java.io.IOException("code " + code);
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                final byte[] data = bos.toByteArray();
                if (seq != imageReqSeq) return;
                final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bmp == null) throw new java.io.IOException("decode fail");
                handler.post(() -> {
                    if (mode == MODE_IMAGE && seq == imageReqSeq) {
                        imageView.setImageBitmap(bmp);
                        imageLoading = false;
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (mode == MODE_IMAGE && seq == imageReqSeq) {
                        if (imageRetry < 1) {
                            imageRetry++;
                            loadImage(uri);
                        } else {
                            imageView.setImageDrawable(null);
                            tvStatus.setText(R.string.main_image_fail);
                            imageLoading = false;
                        }
                    }
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "etcas-img");
        t.start();
    }

    private void enterMirror(String uri) {
        mode = MODE_MIRROR;
        handler.removeCallbacks(loadTicker);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        playerView.setVisibility(View.GONE);
        loadingRoot.setVisibility(View.GONE);
        infoPanel.setVisibility(View.GONE);
        mirrorImage.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.main_casting);
        String frameUrl;
        try {
            frameUrl = Uri.parse(uri).getQueryParameter("u");
        } catch (Exception e) {
            frameUrl = null;
        }
        startMirror(frameUrl);
    }

    private void startMirror(final String frameUrl) {
        if (frameUrl == null || frameUrl.isEmpty()) return;
        stopMirror();
        mirroring = true;
        final boolean multipart = frameUrl.contains("/mirror");
        mirrorThread = new Thread(() -> {
            while (mirroring) {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(frameUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(multipart ? 60000 : 3000);
                    conn.setRequestProperty("Connection", "close");
                    int code = conn.getResponseCode();
                    if (code != 200) throw new java.io.IOException("code " + code);
                    java.io.BufferedInputStream bin =
                            new java.io.BufferedInputStream(conn.getInputStream(), 65536);
                    if (multipart) {
                        while (mirroring) {
                            String boundary = readAsciiLine(bin);
                            if (boundary == null || !boundary.startsWith("--etcasframe")) break;
                            if (boundary.startsWith("--etcasframe--")) break;
                            int len = -1;
                            String h;
                            while ((h = readAsciiLine(bin)) != null && !h.isEmpty()) {
                                int c = h.indexOf(':');
                                if (c > 0 && h.substring(0, c).trim().equalsIgnoreCase("Content-Length")) {
                                    try {
                                        len = Integer.parseInt(h.substring(c + 1).trim());
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                            if (len <= 0) break;
                            byte[] data = new byte[len];
                            int off = 0;
                            while (off < len) {
                                int r = bin.read(data, off, len - off);
                                if (r < 0) break;
                                off += r;
                            }
                            if (off < len) break;
                            bin.read();
                            bin.read();
                            final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, len);
                            if (bmp != null) {
                                runOnUiThread(() -> {
                                    if (mode == MODE_MIRROR && mirroring) mirrorImage.setImageBitmap(bmp);
                                });
                            }
                        }
                    } else {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[32768];
                        int n;
                        while ((n = bin.read(buf)) > 0) bos.write(buf, 0, n);
                        byte[] data = bos.toByteArray();
                        if (data.length > 0) {
                            final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                            if (bmp != null) {
                                runOnUiThread(() -> {
                                    if (mode == MODE_MIRROR && mirroring) mirrorImage.setImageBitmap(bmp);
                                });
                            }
                        }
                        try {
                            Thread.sleep(60);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        break;
                    }
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "etcas-tv-mirror");
        mirrorThread.setDaemon(true);
        mirrorThread.start();
    }

    private static String readAsciiLine(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(96);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') bos.write(c);
        }
        if (c == -1 && bos.size() == 0) return null;
        return bos.toString("UTF-8");
    }

    private void stopMirror() {
        mirroring = false;
        if (mirrorThread != null) {
            mirrorThread.interrupt();
            mirrorThread = null;
        }
    }

    private static boolean isImageUri(String uri) {
        if (uri == null) return false;
        try {
            String lower = uri.toLowerCase();
            String[] exts = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"};
            for (String e : exts) {
                if (lower.contains(e)) return true;
            }
            return lower.contains("img=1") || lower.contains("img=1&");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mode == MODE_VIDEO && player != null) {
                if (player.isPlaying()) {
                    player.pause();
                    CastState.get().setPlaying(false);
                } else {
                    player.play();
                    CastState.get().setPlaying(true);
                }
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (mode != MODE_INFO) {
                CastState.get().clear();
                enterInfo();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(loadTicker);
        handler.removeCallbacks(progressTicker);
        stopMirror();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
