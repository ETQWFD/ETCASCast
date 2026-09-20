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

    private PlayerView playerView;
    private ExoPlayer player;
    private View infoPanel;
    private View loadingRoot;
    private TextView loadingText;
    private ImageView mirrorImage;
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

    private final CastState.Listener stateListener = new CastState.Listener() {
        @Override
        public void onMediaChanged(String uri, String title) {
            if (uri == null || uri.isEmpty()) return;
            if (uri.startsWith("etcas://mirror")) {
                enterMirror(uri);
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

        player = new ExoPlayer.Builder(this).build();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        CastState.get().register(stateListener);
        bindInfo();
        String u = CastState.get().getUri();
        if (u != null && !u.isEmpty()) {
            if (u.startsWith("etcas://mirror")) enterMirror(u);
            else enterVideo(u, CastState.get().getTitle());
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
        infoPanel.setVisibility(View.GONE);
        loadingRoot.setVisibility(View.VISIBLE);
        loadIndex = 0;
        handler.removeCallbacks(loadTicker);
        loadingText.setText(loadLines[0]);
        loadIndex = 1;
        handler.postDelayed(loadTicker, 2000);
        tvStatus.setText(R.string.main_loading);
    }

    private void enterVideo(String uri, String title) {
        mode = MODE_VIDEO;
        handler.removeCallbacks(loadTicker);
        stopMirror();
        mirrorImage.setVisibility(View.GONE);
        loadingRoot.setVisibility(View.GONE);
        infoPanel.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        if (player != null) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)));
            player.prepare();
            player.play();
        }
        tvStatus.setText(getString(R.string.main_casting) + (title == null || title.isEmpty() ? "" : " · " + title));
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
        mirrorThread = new Thread(() -> {
            while (mirroring) {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(frameUrl + (frameUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis());
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(3000);
                    conn.setRequestProperty("Connection", "close");
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        InputStream in = conn.getInputStream();
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[32768];
                        int n;
                        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                        byte[] data = bos.toByteArray();
                        if (data.length > 0) {
                            final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                            if (bmp != null) runOnUiThread(() -> {
                                if (mode == MODE_MIRROR) mirrorImage.setImageBitmap(bmp);
                            });
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "etcas-tv-mirror");
        mirrorThread.setDaemon(true);
        mirrorThread.start();
    }

    private void stopMirror() {
        mirroring = false;
        if (mirrorThread != null) {
            mirrorThread.interrupt();
            mirrorThread = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MENU) {
            if (mode != MODE_INFO) {
                enterInfo();
                CastState.get().clear();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(loadTicker);
        stopMirror();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
