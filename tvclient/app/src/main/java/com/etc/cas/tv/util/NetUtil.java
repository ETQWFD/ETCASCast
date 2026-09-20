package com.etc.cas.tv.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public final class NetUtil {

    private NetUtil() {
    }

    public static boolean isNetworkConnected(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectManagerSafe(ctx));
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= 23) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
            } else {
                NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static ConnectivityManager ConnectManagerSafe(Context ctx) {
        return (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static String localIpv4() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName().toLowerCase();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String ip = a.getHostAddress();
                        if (fallback == null) fallback = ip;
                        if (name.startsWith("wlan") || name.startsWith("wifi")
                                || name.startsWith("eth") || name.startsWith("ap")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback != null ? fallback : "0.0.0.0";
    }
}
