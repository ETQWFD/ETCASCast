package com.etc.cas.cast;

import com.etc.cas.discovery.CastDevice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CastManager {

    private static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String REND = "urn:schemas-upnp-org:service:RenderingControl:1";

    public static boolean setUri(CastDevice d, String uri, String meta) {
        String safeUri = uri == null ? "" : uri.replace("&", "&amp;");
        String args = "<InstanceID>0</InstanceID>"
                + "<CurrentURI>" + safeUri + "</CurrentURI>"
                + "<CurrentURIMetaData>" + (meta == null ? "" : meta) + "</CurrentURIMetaData>";
        return soap(d.controlUrl, AVT, "SetAVTransportURI", args);
    }

    public static boolean play(CastDevice d) {
        return soap(d.controlUrl, AVT, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
    }

    public static boolean pause(CastDevice d) {
        return soap(d.controlUrl, AVT, "Pause", "<InstanceID>0</InstanceID>");
    }

    public static boolean stop(CastDevice d) {
        return soap(d.controlUrl, AVT, "Stop", "<InstanceID>0</InstanceID>");
    }

    public static boolean cast(CastDevice d, String uri, String meta) {
        if (d == null) return false;
        if (d.controlUrl == null) {
            return dialWake(d);
        }
        soap(d.controlUrl, AVT, "Stop", "<InstanceID>0</InstanceID>");
        if (!setUri(d, uri, meta)) return false;
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }
        return play(d);
    }

    private static boolean dialWake(CastDevice d) {
        if (d.dialAppUrl == null || d.dialAppUrl.isEmpty()) return false;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(d.dialAppUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ETCASCast/1.4");
            int code = conn.getResponseCode();
            return code > 0 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static boolean setVolume(CastDevice d, int volume) {
        if (d.volumeControlUrl == null) return false;
        int v = Math.max(0, Math.min(100, volume));
        return soap(d.volumeControlUrl, REND, "SetVolume",
                "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>" + v + "</DesiredVolume>");
    }

    public static boolean setSpeed(CastDevice d, float speed) {
        if (d == null) return false;
        float rate = Math.max(0.25f, Math.min(2.0f, speed));
        String param = java.net.URLEncoder.encode(String.valueOf(rate), java.nio.charset.StandardCharsets.UTF_8);
        if (d.etcas) {
            String base = d.location != null ? d.location : ("http://" + d.ip + ":" + d.port + "/rootDesc.xml");
            String host = base;
            try {
                java.net.URL u = new java.net.URL(base);
                int p = u.getPort() > 0 ? u.getPort() : (d.port > 0 ? d.port : 9170);
                host = "http://" + u.getHost() + ":" + p;
            } catch (Exception ignored) {
            }
            HttpURLConnection conn = null;
            try {
                URL url = new URL(host + "/etcas/speed");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                byte[] body = ("rate=" + param).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                conn.getOutputStream().write(body);
                int code = conn.getResponseCode();
                return code == 200;
            } catch (Exception e) {
                return false;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        if (d.controlUrl == null) return false;
        return soap(d.controlUrl, AVT, "SetPlaySpeed",
                "<InstanceID>0</InstanceID><Speed>" + rate + "</Speed>");
    }

    private static boolean soap(String controlUrl, String service, String action, String args) {
        if (controlUrl == null || controlUrl.isEmpty()) return false;
        HttpURLConnection conn = null;
        try {
            String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                    + "<s:Body><u:" + action + " xmlns:u=\"" + service + "\">"
                    + args
                    + "</u:" + action + "></s:Body></s:Envelope>";

            URL url = new URL(controlUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPACTION", "\"" + service + "#" + action + "\"");
            conn.setRequestProperty("Connection", "close");
            conn.setDoOutput(true);

            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                String resp = bos.toString("UTF-8");
                if (code == 200) {
                    if (resp.isEmpty()) return true;
                    String low = resp.toLowerCase();
                    if (low.contains("errorcode") || (low.contains("fault") && !low.contains(":response"))) {
                        return false;
                    }
                    return true;
                }
                return false;
            }
            return code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
