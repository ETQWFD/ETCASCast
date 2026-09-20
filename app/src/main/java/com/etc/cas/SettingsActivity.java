package com.etc.cas;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class SettingsActivity extends BaseActivity {

    private View themeSakura;
    private View themeSea;
    private View themeOrange;
    private View themeEmerald;
    private View themeSakuraFrame;
    private View themeSeaFrame;
    private View themeOrangeFrame;
    private View themeEmeraldFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        themeSakura = findViewById(R.id.theme_sakura);
        themeSea = findViewById(R.id.theme_sea);
        themeOrange = findViewById(R.id.theme_orange);
        themeEmerald = findViewById(R.id.theme_emerald);
        themeSakuraFrame = findViewById(R.id.theme_sakura_frame);
        themeSeaFrame = findViewById(R.id.theme_sea_frame);
        themeOrangeFrame = findViewById(R.id.theme_orange_frame);
        themeEmeraldFrame = findViewById(R.id.theme_emerald_frame);

        themeSakura.setOnClickListener(v -> pickTheme(Prefs.THEME_SAKURA));
        themeSea.setOnClickListener(v -> pickTheme(Prefs.THEME_SEA));
        themeOrange.setOnClickListener(v -> pickTheme(Prefs.THEME_ORANGE));
        themeEmerald.setOnClickListener(v -> pickTheme(Prefs.THEME_EMERALD));

        findViewById(R.id.chip_mode_light).setOnClickListener(v -> pickMode(Prefs.MODE_LIGHT));
        findViewById(R.id.chip_mode_dark).setOnClickListener(v -> pickMode(Prefs.MODE_DARK));
        findViewById(R.id.chip_mode_system).setOnClickListener(v -> pickMode(Prefs.MODE_SYSTEM));

        findViewById(R.id.chip_font_sc).setOnClickListener(v -> pickFont(Prefs.FONT_SC));
        findViewById(R.id.chip_font_en).setOnClickListener(v -> pickFont(Prefs.FONT_EN));
        findViewById(R.id.chip_font_tc).setOnClickListener(v -> pickFont(Prefs.FONT_TC));
        findViewById(R.id.chip_font_jp).setOnClickListener(v -> pickFont(Prefs.FONT_JP));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.row_update).setOnClickListener(v ->
                UpdateChecker.check(this));

        findViewById(R.id.row_license).setOnClickListener(v -> showLicense());
        findViewById(R.id.row_website).setOnClickListener(v -> openWebsite());
        findViewById(R.id.row_tribute).setOnClickListener(v -> showTribute());

        ((TextView) findViewById(R.id.tv_dev_version))
                .setText(getString(R.string.version_fmt, BuildConfig.VERSION_NAME));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        FontManager.apply(findViewById(android.R.id.content), this);
        refreshState();
    }

    private void refreshState() {
        String theme = Prefs.theme(this);
        markTheme(themeSakuraFrame, Prefs.THEME_SAKURA.equals(theme));
        markTheme(themeSeaFrame, Prefs.THEME_SEA.equals(theme));
        markTheme(themeOrangeFrame, Prefs.THEME_ORANGE.equals(theme));
        markTheme(themeEmeraldFrame, Prefs.THEME_EMERALD.equals(theme));

        String mode = Prefs.mode(this);
        ThemeManager.colorChip(this, findViewById(R.id.chip_mode_light), Prefs.MODE_LIGHT.equals(mode));
        ThemeManager.colorChip(this, findViewById(R.id.chip_mode_dark), Prefs.MODE_DARK.equals(mode));
        ThemeManager.colorChip(this, findViewById(R.id.chip_mode_system), Prefs.MODE_SYSTEM.equals(mode));

        String font = Prefs.font(this);
        ThemeManager.colorChip(this, findViewById(R.id.chip_font_sc), Prefs.FONT_SC.equals(font));
        ThemeManager.colorChip(this, findViewById(R.id.chip_font_en), Prefs.FONT_EN.equals(font));
        ThemeManager.colorChip(this, findViewById(R.id.chip_font_tc), Prefs.FONT_TC.equals(font));
        ThemeManager.colorChip(this, findViewById(R.id.chip_font_jp), Prefs.FONT_JP.equals(font));
    }

    private void markTheme(View frame, boolean selected) {
        int accent = ThemeManager.accent(this);
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ring.setStroke(dp(3), selected ? accent : 0x00000000);
        frame.setBackground(ring);
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private void pickTheme(String theme) {
        Prefs.setTheme(this, theme);
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        onResume();
    }

    private void pickMode(String mode) {
        Prefs.setMode(this, mode);
        ThemeManager.applyNightMode(this);
        recreate();
    }

    private void pickFont(String font) {
        Prefs.setFont(this, font);
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void openWebsite() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://etc.os.kg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "https://etc.os.kg", Toast.LENGTH_LONG).show();
        }
    }

    private void showTribute() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (d * 22);
        box.setPadding(pad, (int) (d * 18), pad, 0);

        TextView title = new TextView(this);
        title.setText(R.string.dev_tribute_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        box.addView(title);

        TextView body = new TextView(this);
        body.setText(R.string.dev_tribute_body);
        body.setTextColor(getColor(R.color.text_primary));
        body.setTextSize(15);
        body.setLineSpacing(d * 4, 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = (int) (d * 12);
        body.setLayoutParams(bp);
        box.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(box)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.show();
        FontManager.apply(box, this);
        Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btn != null) FontManager.apply(btn, this);
    }

    private void showLicense() {
        View content = getLayoutInflater().inflate(R.layout.dialog_license, null);
        TextView tvLicense = content.findViewById(R.id.tv_license_text);
        tvLicense.setText(getString(R.string.license_text));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(true)
                .create();
        content.findViewById(R.id.btn_license_close).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
