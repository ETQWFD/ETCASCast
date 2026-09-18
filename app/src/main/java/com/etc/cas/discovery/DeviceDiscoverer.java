package com.etc.cas.discovery;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
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
    private static final String[] PROBE_PATHS = {
            "/rootDesc.xml", "/description.xml", "/upnp/description.xml",
            "/DeviceDescription.xml", "/device/description.xml", "/rootDesc/description.xml"
    };
    private static final int[] PROBE_PORTS = {80, 1900, 8080, 8060, 9000, 49152, 49153, 49154, 36666, 5000};

    private final Handler main = new Handler(Looper.getMainLooper());

    public void start(Context ctx, Listener listener) {
        new Thread(() -> {
            List<CastDevice> devices = discover(ctx);
            main.post(() -> listener.onDevices(devices));
        }, "etcas-discover").start();
    }

    public void probeIp(Context ctx, final String ip, Listener listener) {
        new Thread(() -> {
            List<CastDevice> found = new ArrayList<>();
            CastDevice d = probeOne(ip);
            if (d != null) found.add(d);
            final List<CastDevice> result = found;
            main.post(() -> listener.onDevices(result));
        }, "etcas-probe").start();
    }

    private List<CastDevice> discover(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        MulticastSocket socket = null;
        List<CastDevice> result = new ArrayList<>();
        try {
            if (wm != null) {
                lock = wm.createMulticastLock("etcas-ssdp");
                lock.setReferenceCounted(false);
                lock.acquire();
            }

            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            socket.setSoTimeout(1500);

            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            NetworkInterface nif = multicastInterface();
            if (nif != null) {
                try {
                    socket.joinGroup(new InetSocketAddress(group, SSDP_PORT), nif);
                } catch (IOException ignored) {
                }
            } else {
                try {
                    socket.joinGroup(group);
                } catch (IOException ignored) {
                }
            }

            Set<String> seen = new LinkedHashSet<>();

            long end = System.currentTimeMillis() + 10000;
            long lastSend = 0;

            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() - lastSend > 1200) {
                    for (String st : SEARCH_TARGETS) {
                        String msg = "M-SEARCH * HTTP/1.1\r\n"
                                + "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n"
                                + "MAN: \"ssdp:discover\"\r\n"
                                + "MX: 2\r\n"
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
                            if (url.startsWith("http")) seen.add(url);
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }

            int count = 0;
            for (String loc : seen) {
                if (count >= 20) break;
                CastDevice d = DeviceInfoParser.parse(loc);
                if (d != null && !containsUrl(result, d.controlUrl)) {
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

    private CastDevice probeOne(String ip) {
        String clean = ip.trim();
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            try {
                java.net.URL u = new java.net.URL(clean);
                return DeviceInfoParser.parse(u.toString());
            } catch (Exception ignored) {
                return null;
            }
        }
        int colon = clean.indexOf(':');
        String host = colon > 0 ? clean.substring(0, colon) : clean;
        for (int port : PROBE_PORTS) {
            for (String path : PROBE_PATHS) {
                CastDevice d = DeviceInfoParser.parse("http://" + host + ":" + port + path);
                if (d != null) return d;
            }
        }
        return null;
    }

    private static boolean containsUrl(List<CastDevice> list, String url) {
        if (url == null) return false;
        for (CastDevice d : list) {
            if (url.equals(d.controlUrl)) return true;
        }
        return false;
    }

    private static NetworkInterface multicastInterface() {
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                if (!ni.supportsMulticast()) continue;
                String name = ni.getName().toLowerCase();
                if (name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("ap") || name.startsWith("eth")) {
                    return ni;
                }
            }
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) return ni;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
