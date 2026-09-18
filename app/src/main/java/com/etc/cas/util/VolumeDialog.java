package com.etc.cas.util;

import android.content.Context;
import android.media.AudioManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.etc.cas.R;
import com.etc.cas.ThemeManager;
import com.etc.cas.cast.CastManager;
import com.etc.cas.discovery.CastDevice;

public class VolumeDialog {

    public static void show(Context ctx, CastDevice device) {
        View content = LayoutInflater.from(ctx).inflate(R.layout.dialog_volume, null);
        SeekBar sbDevice = content.findViewById(R.id.sb_device);
        SeekBar sbSystem = content.findViewById(R.id.sb_system);
        TextView tvDeviceVal = content.findViewById(R.id.tv_device_val);
        TextView tvSystemVal = content.findViewById(R.id.tv_system_val);

        int accent = ThemeManager.accent(ctx);
        sbDevice.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        sbDevice.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        sbSystem.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        sbSystem.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));

        boolean hasDevice = device != null && device.volumeControlUrl != null;

        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int sysMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int sysCur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        sbSystem.setMax(sysMax);
        sbSystem.setProgress(sysCur);
        tvSystemVal.setText(String.valueOf(sysCur));

        sbDevice.setEnabled(hasDevice);
        if (hasDevice) {
            sbDevice.setMax(100);
            sbDevice.setProgress(50);
            tvDeviceVal.setText("50");
        } else {
            tvDeviceVal.setText(ctx.getString(R.string.no_device_hint));
        }

        sbDevice.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvDeviceVal.setText(String.valueOf(progress));
                if (fromUser && device != null) {
                    CastManager.setVolume(device, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        sbSystem.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSystemVal.setText(String.valueOf(progress));
                if (fromUser) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.session_volume)
                .setView(content)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
