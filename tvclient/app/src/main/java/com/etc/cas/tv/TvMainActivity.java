package com.etc.cas.tv;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
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

public class TvMainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private View infoPanel;
    private ImageView qrImage;
    private TextView tvIp;
    private TextView tvKey;
    private TextView tvDevice;
    private TextView tvStatus;

    private final CastState.Listener stateListener = new CastState.Listener() {
        @Override
        public void onMediaChanged(String uri, String title) {
            if (uri == null || uri.isEmpty()) return;
            if (player != null) {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)));
                player.prepare();
                player.play();
            }
            tvStatus.setText(getString(R.string.main_casting) + (title == null || title.isEmpty() ? "" : " · " + title));
            showPanel(false);
        }

        @Override
        public void onPlayStateChanged(boolean playing) {
            if (player == null) return;
            if (playing) {
                player.play();
                tvStatus.setText(R.string.main_casting);
                showPanel(false);
            } else {
                player.pause();
                tvStatus.setText(R.string.main_waiting);
                showPanel(true);
            }
        }

        @Override
        public void onVolumeChanged(int volume) {
            if (player != null) {
                player.setVolume(volume / 100f);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_tv_main);

        playerView = findViewById(R.id.player_view);
        infoPanel = findViewById(R.id.info_panel);
        qrImage = findViewById(R.id.img_qr);
        tvIp = findViewById(R.id.tv_ip);
        tvKey = findViewById(R.id.tv_key);
        tvDevice = findViewById(R.id.tv_device);
        tvStatus = findViewById(R.id.tv_status);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
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
        if (CastState.get().isPlaying() && CastState.get().getUri() != null) {
            showPanel(false);
        } else {
            showPanel(true);
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

    private void showPanel(boolean show) {
        infoPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        playerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MENU) {
            if (infoPanel.getVisibility() != View.VISIBLE) {
                showPanel(true);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
