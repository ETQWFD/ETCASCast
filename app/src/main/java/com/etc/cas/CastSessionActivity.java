package com.etc.cas;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.etc.cas.cast.CastManager;
import com.etc.cas.cast.LocalFileServer;
import com.etc.cas.discovery.CastDevice;
import com.etc.cas.util.VolumeDialog;
import com.google.android.material.button.MaterialButton;

import java.io.InputStream;

public class CastSessionActivity extends BaseActivity {

    private static final int QUALITY_AUTO = 0;
    private static final int QUALITY_HD = 1;
    private static final int QUALITY_SD = 2;
    private static final int QUALITY_SMOOTH = 3;

    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webView;
    private ImageView imgImage;
    private FrameLayout playerFrame;
    private View loadingOverlay;
    private ObjectAnimator loadingAnim;

    private CastDevice device;
    private String mode;
    private String mediaUrl;
    private String title;
    private String mime;
    private boolean isDirect;
    private boolean isImage;
    private boolean started;

    private int quality = QUALITY_AUTO;
    private float speed = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast_session);

        mode = getIntent().getStringExtra("mode");
        mediaUrl = getIntent().getStringExtra("uri");
        title = getIntent().getStringExtra("title");
        mime = getIntent().getStringExtra("mime");
        isDirect = getIntent().getBooleanExtra("isDirect", true);
        try {
            device = (CastDevice) getIntent().getSerializableExtra("device");
        } catch (Exception e) {
            device = null;
        }

        if (mime == null) mime = "";
        isImage = mime.startsWith("image/") || (mediaUrl != null && isImageExt(mediaUrl));

        playerView = findViewById(R.id.player_view);
        playerFrame = findViewById(R.id.player_frame);
        imgImage = findViewById(R.id.img_image);
        loadingOverlay = findViewById(R.id.loading_overlay);
        TextView tvStatus = findViewById(R.id.tv_status);

        ImageView imgLoading = findViewById(R.id.img_loading);
        loadingAnim = ObjectAnimator.ofFloat(imgLoading, "rotation", 0f, 360f);
        loadingAnim.setDuration(1100);
        loadingAnim.setRepeatCount(ValueAnimator.INFINITE);
        loadingAnim.setInterpolator(new LinearInterpolator());
        loadingAnim.start();

        if (device != null) {
            tvStatus.setText(getString(R.string.session_ready) + " · " + device.name + "（" + device.type + "）");
        } else {
            tvStatus.setText(R.string.session_ready);
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_start).setOnClickListener(v -> startCasting());
        findViewById(R.id.btn_stop).setOnClickListener(v -> stopCasting());
        findViewById(R.id.btn_volume).setOnClickListener(v -> VolumeDialog.show(this, device));

        setupPlayer();
        setupSpeedChips();
        setupQualityChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        FontManager.apply(findViewById(android.R.id.content), this);
        refreshChips();
        if (started && isDirect && player != null) {
            if (player.getPlaybackState() != androidx.media3.common.Player.STATE_ENDED) {
                player.setPlaybackParameters(new PlaybackParameters(speed, 1f));
                player.play();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    private void setupPlayer() {
        if (isImage) {
            playerView.setVisibility(View.GONE);
            imgImage.setVisibility(View.VISIBLE);
            loadImage();
        } else if (isDirect) {
            try {
                DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1500, 30000, 300, 1000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build();
                player = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
                playerView.setPlayer(player);
                player.addListener(new androidx.media3.common.Player.Listener() {
                    @Override
                    public void onPlayerError(androidx.media3.common.PlaybackException error) {
                        Toast.makeText(CastSessionActivity.this, R.string.session_play_fail, Toast.LENGTH_LONG).show();
                    }
                });
                player.setMediaItem(MediaItem.fromUri(Uri.parse(mediaUrl)));
                player.prepare();
            } catch (Exception e) {
                Toast.makeText(this, R.string.session_play_fail, Toast.LENGTH_LONG).show();
            }
        } else {
            playerView.setVisibility(View.GONE);
            webView = new WebView(this);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            playerFrame.addView(webView, lp);
        }
    }

    private void startCasting() {
        if (started) return;
        started = true;

        String castUrl = mediaUrl;
        if ("local".equals(mode)) {
            String base = LocalFileServer.start(this, Uri.parse(mediaUrl), mime);
            if (base != null) castUrl = LocalFileServer.fileUrl(this);
        }

        if (device != null) {
            final CastDevice dev = device;
            final String url = castUrl;
            final String meta = buildMeta(title, mime);
            new Thread(() -> {
                boolean ok = CastManager.cast(dev, url, meta);
                runOnUiThread(() -> {
                    TextView tv = findViewById(R.id.tv_status);
                    tv.setText(getString(R.string.session_casting_to) + dev.name
                            + (ok ? "" : "（" + getString(R.string.device_not_response) + "）"));
                });
            }, "etcas-cast").start();
        }

        CastKeepAliveService.start(this, title == null || title.isEmpty() ? device == null ? "" : device.name : title);

        if (isImage) {
            imgImage.setVisibility(View.VISIBLE);
        } else if (isDirect && player != null) {
            player.play();
        } else if (webView != null) {
            webView.loadUrl(mediaUrl);
        }

        loadingOverlay.setVisibility(View.GONE);
        findViewById(R.id.btn_start).setEnabled(false);
        findViewById(R.id.btn_start).setAlpha(0.6f);
    }

    private void stopCasting() {
        if (device != null) {
            final CastDevice dev = device;
            new Thread(() -> CastManager.stop(dev), "etcas-cast-stop").start();
        }
        CastKeepAliveService.stop(this);
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        Toast.makeText(this, R.string.session_end, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setupSpeedChips() {
        int[] ids = {R.id.chip_speed_05, R.id.chip_speed_1, R.id.chip_speed_125, R.id.chip_speed_15, R.id.chip_speed_2};
        final float[] speeds = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < ids.length; i++) {
            MaterialButton chip = findViewById(ids[i]);
            final float s = speeds[i];
            chip.setOnClickListener(v -> {
                speed = s;
                for (int j = 0; j < ids.length; j++) {
                    ThemeManager.colorChip(this, findViewById(ids[j]), speeds[j] == s);
                }
                if (player != null) {
                    player.setPlaybackParameters(new PlaybackParameters(s, 1f));
                }
                if (device != null) {
                    final CastDevice dev = device;
                    final float fs = s;
                    new Thread(() -> CastManager.setSpeed(dev, fs), "etcas-speed").start();
                }
            });
        }
    }

    private void setupQualityChips() {
        int[] ids = {R.id.chip_q_auto, R.id.chip_q_hd, R.id.chip_q_sd, R.id.chip_q_smooth};
        for (int i = 0; i < ids.length; i++) {
            MaterialButton chip = findViewById(ids[i]);
            final int q = i;
            chip.setOnClickListener(v -> {
                quality = q;
                for (int j = 0; j < ids.length; j++) {
                    ThemeManager.colorChip(this, findViewById(ids[j]), j == q);
                }
                applyQuality();
            });
        }
    }

    private void refreshChips() {
        int[] speedIds = {R.id.chip_speed_05, R.id.chip_speed_1, R.id.chip_speed_125, R.id.chip_speed_15, R.id.chip_speed_2};
        final float[] speeds = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int j = 0; j < speedIds.length; j++) {
            ThemeManager.colorChip(this, findViewById(speedIds[j]), speeds[j] == speed);
        }
        int[] qualityIds = {R.id.chip_q_auto, R.id.chip_q_hd, R.id.chip_q_sd, R.id.chip_q_smooth};
        for (int j = 0; j < qualityIds.length; j++) {
            ThemeManager.colorChip(this, findViewById(qualityIds[j]), j == quality);
        }
    }

    private void applyQuality() {
        if (player == null) return;
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
        switch (quality) {
            case QUALITY_HD:
                player.setTrackSelectionParameters(b
                        .setMaxVideoSize(1920, 1080)
                        .setMaxVideoBitrate(8_000_000)
                        .build());
                break;
            case QUALITY_SD:
                player.setTrackSelectionParameters(b
                        .setMaxVideoSize(1280, 720)
                        .setMaxVideoBitrate(3_000_000)
                        .build());
                break;
            case QUALITY_SMOOTH:
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
    }

    private void loadImage() {
        new Thread(() -> {
            try {
                Bitmap bmp;
                if (mediaUrl.startsWith("content://")) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    try (InputStream is = getContentResolver().openInputStream(Uri.parse(mediaUrl))) {
                        BitmapFactory.decodeStream(is, null, opts);
                    }
                    int sample = 1;
                    while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2;
                    try (InputStream is2 = getContentResolver().openInputStream(Uri.parse(mediaUrl))) {
                        opts.inJustDecodeBounds = false;
                        opts.inSampleSize = sample;
                        bmp = BitmapFactory.decodeStream(is2, null, opts);
                    }
                } else {
                    bmp = BitmapFactory.decodeStream(new java.net.URL(mediaUrl).openStream());
                }
                final Bitmap fb = bmp;
                runOnUiThread(() -> {
                    if (fb != null) imgImage.setImageBitmap(fb);
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private boolean isImageExt(String url) {
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png")
                || u.endsWith(".gif") || u.endsWith(".webp") || u.endsWith(".bmp");
    }

    private String buildMeta(String title, String mime) {
        String cls = "object.item.videoItem";
        if (mime != null && mime.startsWith("image/")) cls = "object.item.imageItem";
        else if (mime != null && mime.startsWith("audio/")) cls = "object.item.audioItem";
        String safeTitle = title == null ? "" : title
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                + "<item id=\"0\" parentID=\"-1\" restricted=\"1\">"
                + "<dc:title>" + safeTitle + "</dc:title>"
                + "<upnp:class>" + cls + "</upnp:class>"
                + "</item></DIDL-Lite>";
    }

    @Override
    protected void onDestroy() {
        if (loadingAnim != null) loadingAnim.cancel();
        if (player != null) {
            player.release();
            player = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        if (!started && "local".equals(mode)) {
            LocalFileServer.stop();
        }
        super.onDestroy();
    }
}
