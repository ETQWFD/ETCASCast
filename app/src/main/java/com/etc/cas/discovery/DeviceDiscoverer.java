package com.etc.cas.discovery;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DeviceDiscoverer {

    public interface Listener {
        void onDevices(List<CastDevice> devices);
    }

    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String[] SEARCH_TARGETS = {
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "ssdp:all"
    };

    private final Handler main = new Handler(Looper.getMainLooper());

    public void start(Context ctx, Listener listener) {
        new Thread(() -> {
            List<CastDevice> devices = discover(ctx);
            main.post(() -> listener.onDevices(devices));
        }, "etcas-discover").start();
    }

    private List<CastDevice> discover(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        DatagramSocket socket = null;
        List<CastDevice> result = new ArrayList<>();
        try {
            if (wm != null) {
                lock = wm.createMulticastLock("etcas-ssdp");
                lock.setReferenceCounted(false);
                lock.acquire();
            }

            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            socket.setSoTimeout(1200);

            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            Set<String> locations = new LinkedHashSet<>();

            long end = System.currentTimeMillis() + 9000;
            long lastSend = 0;

            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() - lastSend > 1500) {
                    for (String st : SEARCH_TARGETS) {
                        String msg = "M-SEARCH * HTTP/1.1\r\n"
                                + "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n"
                                + "MAN: \"ssdp:discover\"\r\n"
                                + "MX: 3\r\n"
                                + "ST: " + st + "\r\n"
                                + "USER-AGENT: ETCASCast/1.4\r\n\r\n";
                        try {
                            DatagramPacket out = new DatagramPacket(msg.getBytes("UTF-8"), msg.length(), group, SSDP_PORT);
                            socket.send(out);
                        } catch (Exception ignored) {
                        }
                    }
                    lastSend = System.currentTimeMillis();
                }

                try {
                    byte[] buf = new byte[8192];
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String text = new String(p.getData(), 0, p.getLength(), "UTF-8");
                    for (String line : text.split("\r?\n")) {
                        int idx = line.toLowerCase().indexOf("location:");
                        if (idx >= 0) {
                            String url = line.substring(idx + 9).trim();
                            if (url.startsWith("http")) locations.add(url);
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }

            int count = 0;
            for (String loc : locations) {
                if (count >= 15) break;
                CastDevice d = DeviceInfoParser.parse(loc);
                if (d != null) {
                    result.add(d);
                    count++;
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {
            }
            try {
                if (lock != null) lock.release();
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
