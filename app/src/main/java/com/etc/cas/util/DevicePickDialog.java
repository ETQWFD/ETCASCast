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
    }

    public static AlertDialog show(Context ctx, List<CastDevice> devices, Callback cb) {
        View content = LayoutInflater.from(ctx).inflate(R.layout.dialog_device_list, null);
        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.select_device_title)
                .setView(content)
                .setCancelable(true)
                .create();
        content.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        applyState(dialog, devices, cb);
        dialog.show();
        return dialog;
    }

    public static void update(AlertDialog dialog, List<CastDevice> devices, Callback cb) {
        if (dialog == null || !dialog.isShowing()) return;
        applyState(dialog, devices, cb);
    }

    private static void applyState(AlertDialog dialog, List<CastDevice> devices, Callback cb) {
        Context ctx = dialog.getContext();
        ListView list = dialog.findViewById(R.id.list_devices);
        ProgressBar spinner = dialog.findViewById(R.id.pb_search);
        TextView tvSearching = dialog.findViewById(R.id.tv_searching);
        View panelEmpty = dialog.findViewById(R.id.panel_empty);
        TextView tvEmpty = dialog.findViewById(R.id.tv_empty);
        TextView tvHint = dialog.findViewById(R.id.tv_hint);
        MaterialButton btnRefresh = dialog.findViewById(R.id.btn_refresh);
        ImageView icon = dialog.findViewById(R.id.iv_dialog_icon);

        int accent = ThemeManager.accent(ctx);
        icon.setColorFilter(accent);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(accent));
        btnRefresh.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.surface)));
        btnRefresh.setTextColor(ctx.getColor(R.color.text_primary));

        boolean searching = devices == null;
        boolean empty = !searching && devices.isEmpty();

        spinner.setVisibility(searching ? View.VISIBLE : View.GONE);
        tvSearching.setVisibility(searching ? View.VISIBLE : View.GONE);
        panelEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        btnRefresh.setVisibility(searching ? View.GONE : View.VISIBLE);
        list.setVisibility(searching || empty ? View.GONE : View.VISIBLE);

        if (devices != null) {
            DeviceAdapter adapter = new DeviceAdapter(ctx, devices);
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, view, position, id) -> {
                CastDevice d = (CastDevice) adapter.getItem(position);
                dialog.dismiss();
                if (cb != null && d != null) cb.onPick(d);
            });
        }

        btnRefresh.setOnClickListener(v -> {
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
