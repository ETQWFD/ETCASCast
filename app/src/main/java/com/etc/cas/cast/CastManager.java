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
        String args = "<InstanceID>0</InstanceID>"
                + "<CurrentURI>" + xmlEscape(uri) + "</CurrentURI>"
                + "<CurrentURIMetaData>" + xmlEscape(meta == null ? "" : meta) + "</CurrentURIMetaData>";
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

    public static boolean setVolume(CastDevice d, int volume) {
        if (d.volumeControlUrl == null) return false;
        int v = Math.max(0, Math.min(100, volume));
        return soap(d.volumeControlUrl, REND, "SetVolume",
                "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>" + v + "</DesiredVolume>");
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
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPACTION", "\"" + service + "#" + action + "\"");
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
                    return resp.isEmpty() || resp.contains("200 OK") || resp.contains(":Response");
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

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
