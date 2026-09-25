package com.etc.cas.tv.receiver;

import android.net.wifi.WifiManager;
import android.util.Log;

import com.etc.cas.tv.util.NetUtil;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

public class SsdpServer {

    private static final String GROUP = "239.255.255.250";
    private static final int PORT = 1900;
    private static final String DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    private static final String SERVICE_AVT = "urn:schemas-upnp-org:service:AVTransport:1";

    private final int httpPort;
    private final String udn;
    private final String friendlyName;
    private final WifiManager.MulticastLock lock;

    private MulticastSocket socket;
    private Thread listenThread;
    private Thread aliveThread;
    private volatile boolean running;

    public SsdpServer(int httpPort, String udn, String friendlyName, WifiManager.MulticastLock lock) {
        this.httpPort = httpPort;
        this.udn = udn;
        this.friendlyName = friendlyName;
        this.lock = lock;
    }

    public void start() {
        try {
            if (lock != null && !lock.isHeld()) {
                lock.setReferenceCounted(false);
                lock.acquire();
            }
            socket = new MulticastSocket(PORT);
            socket.setReuseAddress(true);
            socket.setSoTimeout(2000);
            try {
                InetAddress group = InetAddress.getByName(GROUP);
                NetworkInterface nif = nif();
                if (nif != null) socket.joinGroup(new InetSocketAddress(group, PORT), nif);
                else socket.joinGroup(group);
            } catch (Exception ignored) {
            }
            running = true;
            listenThread = new Thread(this::listenLoop, "etcas-tv-ssdp");
            listenThread.setDaemon(true);
            listenThread.start();
            aliveThread = new Thread(this::aliveLoop, "etcas-tv-notify");
            aliveThread.setDaemon(true);
            aliveThread.start();
            sendNotify(true);
        } catch (Exception e) {
            Log.d("ETCASTV", "ssdp start error: " + e.getMessage());
        }
    }

    private void listenLoop() {
        byte[] buf = new byte[8192];
        while (running) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);
                String text = new String(dp.getData(), 0, dp.getLength(), "UTF-8");
                String low = text.toLowerCase();
                if (!low.startsWith("m-search")) continue;
                if (matchesTarget(text)) {
                    reply(dp.getAddress(), dp.getPort(), text);
                }
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    private boolean matchesTarget(String text) {
        String low = text.toLowerCase();
        return low.contains("ssdp:all") || low.contains("media renderer")
                || low.contains("upnp:rootdevice") || low.contains(DEVICE_TYPE)
                || low.contains("avtransport") || low.contains("dial-multiscreen")
                || low.contains("device:basic");
    }

    private void reply(InetAddress addr, int port, String request) {
        try {
            String st = DEVICE_TYPE;
            String low = request.toLowerCase();
            int si = low.indexOf("st:");
            if (si >= 0) {
                String v = request.substring(si + 3).trim();
                int cr = v.indexOf('\r');
                int nl = v.indexOf('\n');
                int cut = cr > 0 ? cr : nl;
                if (cut > 0) v = v.substring(0, cut);
                v = v.trim();
                if (!v.isEmpty()) st = v;
            }
            try {
                Thread.sleep((long) (Math.random() * 100));
            } catch (InterruptedException ignored) {
                return;
            }
            String msg = "HTTP/1.1 200 OK\r\n"
                    + "CACHE-CONTROL: max-age=1800\r\n"
                    + "DATE: \r\n"
                    + "EXT:\r\n"
                    + "LOCATION: " + location() + "\r\n"
                    + "SERVER: Linux/1.0 UPnP/1.0 ETCASCastTV/1.0\r\n"
                    + "ST: " + st + "\r\n"
                    + "USN: " + udn + "::urn:schemas-upnp-org:device:MediaRenderer:1\r\n"
                    + "X-ETCAS-KEY: 1\r\n"
                    + "FRIENDLY-NAME: " + friendlyName + "\r\n\r\n";
            byte[] data = msg.getBytes("UTF-8");
            socket.send(new DatagramPacket(data, data.length, addr, port));
        } catch (Exception ignored) {
        }
    }

    private void aliveLoop() {
        while (running) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException ignored) {
                return;
            }
            if (running) sendNotify(true);
        }
    }

    private void sendNotify(boolean alive) {
        String nts = alive ? "ssdp:alive" : "ssdp:byebye";
        String[] types = {DEVICE_TYPE, SERVICE_AVT, "upnp:rootdevice"};
        for (String t : types) {
            try {
                String msg = "NOTIFY * HTTP/1.1\r\n"
                        + "HOST: " + GROUP + ":" + PORT + "\r\n"
                        + "CACHE-CONTROL: max-age=1800\r\n"
                        + "LOCATION: " + location() + "\r\n"
                        + "NT: " + t + "\r\n"
                        + "NTS: " + nts + "\r\n"
                        + "SERVER: Linux/1.0 UPnP/1.0 ETCASCastTV/1.0\r\n"
                        + "USN: " + udn + (t.equals("upnp:rootdevice") ? "::upnp:rootdevice" : "::" + t)
                        + "\r\n\r\n";
                byte[] data = msg.getBytes("UTF-8");
                DatagramPacket dp = new DatagramPacket(data, data.length,
                        InetAddress.getByName(GROUP), PORT);
                socket.send(dp);
            } catch (Exception ignored) {
            }
        }
    }

    private String location() {
        return "http://" + NetUtil.localIpv4() + ":" + httpPort + "/rootDesc.xml";
    }

    private static NetworkInterface nif() {
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
                String name = ni.getName().toLowerCase();
                if (name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("eth")
                        || name.startsWith("ap")) {
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

    public void stop() {
        running = false;
        try {
            sendNotify(false);
        } catch (Exception ignored) {
        }
        try {
            InetAddress group = InetAddress.getByName(GROUP);
            NetworkInterface nif = nif();
            if (nif != null) socket.leaveGroup(new InetSocketAddress(group, PORT), nif);
            else socket.leaveGroup(group);
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        if (listenThread != null) listenThread.interrupt();
        if (aliveThread != null) aliveThread.interrupt();
        if (lock != null && lock.isHeld()) {
            try {
                lock.release();
            } catch (Exception ignored) {
            }
        }
    }
}
