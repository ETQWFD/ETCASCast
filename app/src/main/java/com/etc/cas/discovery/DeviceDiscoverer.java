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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public class DeviceDiscoverer {

    public interface Listener {
        void onDevices(List<CastDevice> devices, boolean stillSearching);
    }

    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String[] SEARCH_TARGETS = {
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:service:RenderingControl:1",
            "urn:dial-multiscreen-org:service:dial:1",
            "urn:schemas-upnp-org:device:Basic:1",
            "upnp:rootdevice",
            "ssdp:all"
    };
    private static final String[] PROBE_PATHS = {
            "/rootDesc.xml", "/dd.xml", "/description.xml", "/upnp/description.xml",
            "/DeviceDescription.xml", "/device/description.xml", "/rootDesc/description.xml",
            "/xml/device_description.xml", "/dmr.xml", "/devicedesc.xml"
    };
    private static final int[] PROBE_PORTS = {9170, 80, 8080, 8060, 9000, 49152, 49153, 49154, 49155, 36666, 5000, 1900, 52235};

    private static final ThreadFactory DAEMON_FACTORY = r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService parsePool;

    public void start(Context ctx, Listener listener) {
        final Context app = ctx.getApplicationContext();
        Thread t = new Thread(() -> discover(app, listener), "etcas-discover");
        t.setDaemon(true);
        t.start();
    }

    public void probeUrl(Context ctx, final String url, Listener listener) {
        final Context app = ctx.getApplicationContext();
        Thread t = new Thread(() -> {
            List<CastDevice> all = new ArrayList<>();
            post(listener, all, true);
            CastDevice d = DeviceInfoParser.parse(url);
            if (d != null) {
                all.add(d);
                post(listener, all, true);
            }
            post(listener, all, false);
        }, "etcas-probe-url");
        t.setDaemon(true);
        t.start();
    }

    public void probeIp(Context ctx, final String ip, Listener listener) {
        final Context app = ctx.getApplicationContext();
        Thread t = new Thread(() -> {
            List<CastDevice> all = new ArrayList<>();
            post(listener, all, true);
            CastDevice d = probeOne(ip);
            if (d != null) {
                all.add(d);
                post(listener, all, true);
            }
            post(listener, all, false);
        }, "etcas-probe");
        t.setDaemon(true);
        t.start();
    }

    private void discover(Context ctx, Listener listener) {
        WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        MulticastSocket socket = null;
        final List<CastDevice> result = new ArrayList<>();
        final Set<String> seenUrls = new LinkedHashSet<>();
        parsePool = Executors.newFixedThreadPool(10, DAEMON_FACTORY);
        try {
            if (wm != null) {
                lock = wm.createMulticastLock("etcas-ssdp");
                lock.setReferenceCounted(false);
                lock.acquire();
            }

            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            socket.setSoTimeout(1000);

            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            NetworkInterface nif = multicastInterface();
            try {
                if (nif != null) socket.joinGroup(new InetSocketAddress(group, SSDP_PORT), nif);
                else socket.joinGroup(group);
            } catch (IOException ignored) {
            }

            post(listener, result, true);

            final Set<String> locations = new LinkedHashSet<>();
            long end = System.currentTimeMillis() + 12000;
            long lastSend = 0;

            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() - lastSend > 1500) {
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
                    String loc = extractLocation(text);
                    if (loc != null && locations.add(loc)) {
                        parseAsync(loc, d -> {
                            if (d != null && (d.controlUrl != null || d.dial)
                                    && seenUrls.add(deviceKey(d))) {
                                synchronized (result) {
                                    result.add(d);
                                }
                                post(listener, snapshot(result), true);
                            }
                        });
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }

            try {
                if (nif != null) socket.leaveGroup(new InetSocketAddress(group, SSDP_PORT), nif);
                else socket.leaveGroup(group);
            } catch (Exception ignored) {
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
            try {
                if (parsePool != null) {
                    parsePool.shutdown();
                    parsePool.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);
                    parsePool = null;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }
        post(listener, snapshot(result), false);
    }

    private interface ParseCallback {
        void onParsed(CastDevice d);
    }

    private void parseAsync(final String location, final ParseCallback cb) {
        Runnable job = () -> {
            try {
                cb.onParsed(DeviceInfoParser.parse(location));
            } catch (Exception e) {
                cb.onParsed(null);
            }
        };
        ExecutorService p = parsePool;
        if (p != null) p.execute(job);
        else {
            Thread t = new Thread(job, "etcas-desc");
            t.setDaemon(true);
            t.start();
        }
    }

    private static String deviceKey(CastDevice d) {
        if (d.udn != null && !d.udn.isEmpty()) return d.udn;
        if (d.controlUrl != null && !d.controlUrl.isEmpty()) return d.controlUrl;
        if (d.dialAppUrl != null && !d.dialAppUrl.isEmpty()) return d.dialAppUrl;
        return d.location != null ? d.location : (d.ip + ":" + d.port);
    }

    private String extractLocation(String text) {
        for (String line : text.split("\r?\n")) {
            String l = line.toLowerCase();
            int idx = l.indexOf("location:");
            if (idx >= 0) {
                String url = line.substring(idx + 9).trim();
                if (url.startsWith("http")) return url;
            }
        }
        return null;
    }

    private CastDevice probeOne(String input) {
        String clean = input.trim();
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return DeviceInfoParser.parse(clean);
        }
        int colon = clean.indexOf(':');
        final String host = colon > 0 ? clean.substring(0, colon) : clean;

        final AtomicReference<CastDevice> found = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(16, DAEMON_FACTORY);
        List<Future<?>> futures = new ArrayList<>();
        for (final int port : PROBE_PORTS) {
            for (final String path : PROBE_PATHS) {
                futures.add(pool.submit(() -> {
                    if (found.get() != null) return;
                    CastDevice d = DeviceInfoParser.parse("http://" + host + ":" + port + path);
                    if (d != null) found.compareAndSet(null, d);
                }));
            }
        }
        pool.shutdown();
        try {
            pool.awaitTermination(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        for (Future<?> f : futures) f.cancel(true);
        return found.get();
    }

    private static List<CastDevice> snapshot(List<CastDevice> src) {
        List<CastDevice> copy = new ArrayList<>();
        synchronized (src) {
            copy.addAll(src);
        }
        return copy;
    }

    private void post(final Listener listener, final List<CastDevice> devices, final boolean searching) {
        main.post(() -> listener.onDevices(new ArrayList<>(devices), searching));
    }

    private static NetworkInterface multicastInterface() {
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
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
