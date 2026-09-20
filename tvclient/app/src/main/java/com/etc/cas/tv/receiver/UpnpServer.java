package com.etc.cas.tv.receiver;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class UpnpServer {

    private static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String REND = "urn:schemas-upnp-org:service:RenderingControl:1";

    private final String friendlyName;
    private final String udn;
    private final String key;
    private final String model;
    private final String androidVersion;

    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;
    private volatile int boundPort;

    public UpnpServer(String friendlyName, String udn, String key, String model, String androidVersion) {
        this.friendlyName = friendlyName;
        this.udn = udn;
        this.key = key;
        this.model = model;
        this.androidVersion = androidVersion;
    }

    public int start() {
        try {
            ServerSocket ss = null;
            for (int port = 9170; port < 9190; port++) {
                try {
                    ss = new ServerSocket(port);
                    break;
                } catch (Exception ignored) {
                }
            }
            if (ss == null) return -1;
            server = ss;
            boundPort = ss.getLocalPort();
            running = true;
            thread = new Thread(this::acceptLoop, "etcas-tv-http");
            thread.setDaemon(true);
            thread.start();
            return boundPort;
        } catch (Exception e) {
            return -1;
        }
    }

    public int getPort() {
        return boundPort;
    }

    public void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> handle(s), "etcas-tv-conn");
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(10000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String path = parts.length > 1 ? parts[1] : "/";
            String soapAction = "";
            int contentLength = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String low = line.toLowerCase();
                if (low.startsWith("soapaction:")) {
                    soapAction = line.substring(11).trim().replace("\"", "");
                } else if (low.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception ignored) {
                    }
                }
            }
            String body = "";
            if (contentLength > 0) {
                byte[] buf = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = in.read(buf, read, contentLength - read);
                    if (n < 0) break;
                    read += n;
                }
                body = new String(buf, 0, read, StandardCharsets.UTF_8);
            }

            if ("GET".equalsIgnoreCase(method) && (path.startsWith("/rootDesc") || path.startsWith("/etcas/desc"))) {
                writeXml(out, descriptionXml());
            } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/etcas/info")) {
                writeJson(out, "{\"name\":\"" + esc(friendlyName) + "\",\"model\":\"" + esc(model)
                        + "\",\"android\":\"" + esc(androidVersion) + "\",\"key\":\"" + esc(key) + "\"}");
            } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/etcas/pair")) {
                handlePair(out, body);
            } else if ("POST".equalsIgnoreCase(method) && path.contains("AVTransport")) {
                writeXml(out, handleAvt(soapAction, body));
            } else if ("POST".equalsIgnoreCase(method) && path.contains("RenderingControl")) {
                writeXml(out, handleRend(soapAction, body));
            } else {
                String msg = "Not Found";
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: " + data.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(data);
            }
            out.flush();
        } catch (Exception e) {
            Log.d("ETCASTV", "http error: " + e.getMessage());
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String handleAvt(String soapAction, String body) {
        String action = soapAction.contains("#") ? soapAction.substring(soapAction.indexOf('#') + 1) : "";
        switch (action) {
            case "SetAVTransportURI": {
                String uri = tag(body, "CurrentURI");
                String title = tagDcTitle(body);
                if (uri != null && !uri.isEmpty()) {
                    CastState.get().setMedia(uri, title == null ? "" : title);
                }
                return soapResponse(AVT, "SetAVTransportURI");
            }
            case "Play":
                CastState.get().setPlaying(true);
                return soapResponse(AVT, "Play");
            case "Pause":
                CastState.get().setPlaying(false);
                return soapResponse(AVT, "Pause");
            case "Stop":
                CastState.get().clear();
                return soapResponse(AVT, "Stop");
            case "GetPositionInfo":
                return positionInfoResponse();
            case "GetTransportInfo":
                return transportInfoResponse();
            default:
                return soapResponse(AVT, action.isEmpty() ? "Response" : action);
        }
    }

    private void handlePair(OutputStream out, String body) throws Exception {
        String submitted = "";
        if (body != null) {
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && "key".equals(pair.substring(0, eq))) {
                    submitted = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                }
            }
        }
        submitted = submitted.trim().toUpperCase();
        boolean ok = key != null && key.equalsIgnoreCase(submitted);
        if (ok) CastState.get().notifyPaired();
        byte[] data = ("{\"ok\":" + ok + "}").getBytes(StandardCharsets.UTF_8);
        String status = ok ? "200 OK" : "403 Forbidden";
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private String handleRend(String soapAction, String body) {
        String action = soapAction.contains("#") ? soapAction.substring(soapAction.indexOf('#') + 1) : "";
        if ("SetVolume".equals(action)) {
            String v = tag(body, "DesiredVolume");
            try {
                CastState.get().setVolume(v == null ? 80 : Integer.parseInt(v.trim()));
            } catch (Exception ignored) {
            }
        }
        return soapResponse(REND, action.isEmpty() ? "Response" : action);
    }

    private String descriptionXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<root xmlns=\"urn:schemas-upnp-org:device-1-0\" xmlns:etcas=\"urn:etcas-cast:1\">"
                + "<specVersion><major>1</major><minor>0</minor></specVersion>"
                + "<device>"
                + "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>"
                + "<friendlyName>" + esc(friendlyName) + "</friendlyName>"
                + "<manufacturer>ETC Association</manufacturer>"
                + "<manufacturerURL>https://etc.os.kg/</manufacturerURL>"
                + "<modelDescription>ETCAS Cast Receiver</modelDescription>"
                + "<modelName>ETCAS Cast TV</modelName>"
                + "<modelNumber>" + esc(model) + "</modelNumber>"
                + "<modelURL>https://etc.os.kg/</modelURL>"
                + "<UDN>" + udn + "</UDN>"
                + "<etcas:key>" + esc(key) + "</etcas:key>"
                + "<serviceList>"
                + "<service>"
                + "<serviceType>" + AVT + "</serviceType>"
                + "<serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>"
                + "<controlURL>/upnp/control/AVTransport</controlURL>"
                + "<eventSubURL>/upnp/event/AVTransport</eventSubURL>"
                + "<SCPDURL>/upnp/scpd/AVTransport.xml</SCPDURL>"
                + "</service>"
                + "<service>"
                + "<serviceType>" + REND + "</serviceType>"
                + "<serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>"
                + "<controlURL>/upnp/control/RenderingControl</controlURL>"
                + "<eventSubURL>/upnp/event/RenderingControl</eventSubURL>"
                + "<SCPDURL>/upnp/scpd/RenderingControl.xml</SCPDURL>"
                + "</service>"
                + "</serviceList>"
                + "</device></root>";
    }

    private String soapResponse(String service, String action) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + "Response xmlns:u=\"" + service + "\"/></s:Body></s:Envelope>";
    }

    private String positionInfoResponse() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<s:Body><u:GetPositionInfoResponse xmlns:u=\"" + AVT + "\">"
                + "<Track>0</Track><TrackDuration>00:00:00</TrackDuration><TrackURI></TrackURI>"
                + "<RelTime>00:00:00</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>";
    }

    private String transportInfoResponse() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<s:Body><u:GetTransportInfoResponse xmlns:u=\"" + AVT + "\">"
                + "<CurrentTransportState>" + (CastState.get().isPlaying() ? "PLAYING" : "STOPPED")
                + "</CurrentTransportState><CurrentTransportStatus>OK</CurrentTransportStatus>"
                + "<CurrentSpeed>1</CurrentSpeed></u:GetTransportInfoResponse></s:Body></s:Envelope>";
    }

    private static String tag(String xml, String name) {
        if (xml == null) return null;
        String open = "<" + name + ">";
        String close = "</" + name + ">";
        int a = xml.indexOf(open);
        int b = xml.indexOf(close);
        if (a < 0 || b < 0 || b < a) return null;
        return unesc(xml.substring(a + open.length(), b));
    }

    private static String tagDcTitle(String xml) {
        String t = tag(xml, "dc:title");
        if (t != null) return t;
        int a = xml.indexOf("<title>");
        int b = xml.indexOf("</title>");
        if (a >= 0 && b > a) return unesc(xml.substring(a + 7, b));
        return null;
    }

    private static String unesc(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void writeXml(OutputStream out, String xml) throws Exception {
        byte[] data = xml.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/xml; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "EXT:\r\nSERVER: ETCASCastTV/1.0 UPnP/1.0\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private static void writeJson(OutputStream out, String json) throws Exception {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') bos.write(c);
            if (bos.size() > 16384) break;
        }
        return bos.size() == 0 ? null : bos.toString("UTF-8");
    }
}
