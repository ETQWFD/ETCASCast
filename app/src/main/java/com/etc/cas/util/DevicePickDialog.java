package com.etc.cas.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.etc.cas.R;
import com.etc.cas.ThemeManager;
import com.etc.cas.discovery.CastDevice;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class DevicePickDialog {

    public interface Callback {
        void onPick(CastDevice device);

        void onRefresh();

        void onManual();
    }

    public static AlertDialog show(Context ctx, List<CastDevice> devices, Callback cb) {
        try {
            View content = LayoutInflater.from(ctx).inflate(R.layout.dialog_device_list, null);
            AlertDialog dialog = new AlertDialog.Builder(ctx)
                    .setTitle(R.string.select_device_title)
                    .setView(content)
                    .setCancelable(true)
                    .create();
            content.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
            content.findViewById(R.id.btn_manual).setOnClickListener(v -> {
                dialog.dismiss();
                if (cb != null) cb.onManual();
            });
            dialog.show();
            applyState(dialog, null, true, cb);
            return dialog;
        } catch (Exception e) {
            return null;
        }
    }

    public static void update(AlertDialog dialog, List<CastDevice> devices, boolean stillSearching, Callback cb) {
        if (dialog == null || !dialog.isShowing()) return;
        applyState(dialog, devices, stillSearching, cb);
    }

    private static void applyState(AlertDialog dialog, List<CastDevice> devices, boolean stillSearching, Callback cb) {
        Context ctx = dialog.getContext();
        ListView list = dialog.findViewById(R.id.list_devices);
        ProgressBar spinner = dialog.findViewById(R.id.pb_search);
        TextView tvSearching = dialog.findViewById(R.id.tv_searching);
        View panelEmpty = dialog.findViewById(R.id.panel_empty);
        MaterialButton btnRefresh = dialog.findViewById(R.id.btn_refresh);
        ImageView icon = dialog.findViewById(R.id.iv_dialog_icon);

        int accent = ThemeManager.accent(ctx);
        if (icon != null) icon.setColorFilter(accent);
        if (spinner != null) spinner.setIndeterminateTintList(ColorStateList.valueOf(accent));
        if (btnRefresh != null) {
            btnRefresh.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.surface)));
            btnRefresh.setTextColor(ctx.getColor(R.color.text_primary));
        }

        boolean hasDevices = devices != null && !devices.isEmpty();
        boolean empty = !stillSearching && !hasDevices;

        if (spinner != null) spinner.setVisibility(stillSearching ? View.VISIBLE : View.GONE);
        if (tvSearching != null) tvSearching.setVisibility(stillSearching ? View.VISIBLE : View.GONE);
        if (panelEmpty != null) panelEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (btnRefresh != null) btnRefresh.setVisibility(stillSearching ? View.GONE : View.VISIBLE);
        if (list != null) list.setVisibility(hasDevices ? View.VISIBLE : View.GONE);
        if (hasDevices && list != null) {
            DeviceAdapter adapter = new DeviceAdapter(ctx, devices);
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, view, position, id) -> {
                CastDevice d = (CastDevice) adapter.getItem(position);
                dialog.dismiss();
                if (cb != null && d != null) cb.onPick(d);
            });
        }

        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> {
            dialog.dismiss();
            if (cb != null) cb.onRefresh();
        });
    }

    private static class DeviceAdapter extends BaseAdapter {
        private final Context ctx;
        private final List<CastDevice> devices;

        DeviceAdapter(Context ctx, List<CastDevice> devices) {
            this.ctx = ctx;
            this.devices = devices;
        }

        @Override
        public int getCount() {
            return devices == null ? 0 : devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ctx).inflate(R.layout.item_device, parent, false);
            }
            CastDevice d = devices.get(position);
            TextView name = convertView.findViewById(R.id.tv_device_name);
            TextView info = convertView.findViewById(R.id.tv_device_info);
            ImageView icon = convertView.findViewById(R.id.iv_device_icon);
            name.setText(d.name);
            info.setText(d.getInfoLine());
            icon.setColorFilter(ThemeManager.accent(ctx));
            return convertView;
        }
    }
}
