package com.etc.cas;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.cardview.widget.CardView;

import com.etc.cas.util.VolumeDialog;

public class MainActivity extends BaseActivity {

    private static final int MODE_LOCAL = 0;
    private static final int MODE_MIRROR = 1;
    private static final int MODE_LINK = 2;

    private int mode = MODE_LOCAL;
    private CardView cardLocal;
    private CardView cardMirror;
    private CardView cardLink;
    private View dotLocal;
    private View dotMirror;
    private View dotLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardLocal = findViewById(R.id.card_local);
        cardMirror = findViewById(R.id.card_mirror);
        cardLink = findViewById(R.id.card_link);
        dotLocal = findViewById(R.id.dot_local);
        dotMirror = findViewById(R.id.dot_mirror);
        dotLink = findViewById(R.id.dot_link);

        cardLocal.setOnClickListener(v -> {
            mode = MODE_LOCAL;
            refreshCards();
        });
        cardMirror.setOnClickListener(v -> {
            mode = MODE_MIRROR;
            refreshCards();
        });
        cardLink.setOnClickListener(v -> {
            mode = MODE_LINK;
            refreshCards();
        });

        findViewById(R.id.btn_cast).setOnClickListener(v -> startFlow());
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_volume).setOnClickListener(v -> VolumeDialog.show(this, null));
        findViewById(R.id.btn_qr).setOnClickListener(v -> scanQr());

        Eula.showIfNeeded(this, null);
    }

    private void refreshCards() {
        ThemeManager.colorHero(this, findViewById(R.id.hero_card));
        ThemeManager.colorModeCard(this, cardLocal, dotLocal, mode == MODE_LOCAL);
        ThemeManager.colorModeCard(this, cardMirror, dotMirror, mode == MODE_MIRROR);
        ThemeManager.colorModeCard(this, cardLink, dotLink, mode == MODE_LINK);
    }

    private void startFlow() {
        Intent i;
        if (mode == MODE_LOCAL) {
            i = new Intent(this, LocalCastActivity.class);
        } else if (mode == MODE_MIRROR) {
            i = new Intent(this, MirrorActivity.class);
        } else {
            i = new Intent(this, LinkCastActivity.class);
        }
        startActivity(i);
    }

    private void scanQr() {
        startActivity(new Intent(this, QrScanActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        FontManager.apply(findViewById(android.R.id.content), this);
        refreshCards();
    }
}
