package com.etc.cas.discovery;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DeviceInfoParser {

    private static final String AVT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String REND_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1";

    public static CastDevice parse(String location) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(location);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "ETCASCast/1.4");
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) return null;

            InputStream in = conn.getInputStream();
            CastDevice d = new CastDevice();
            d.location = location;

            XmlPullParser xpp = Xml.newPullParser();
            xpp.setInput(in, "UTF-8");

            String serviceType = null;
            String controlUrl = null;
            String modelName = "";
            String deviceType = "";

            int event = xpp.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = xpp.getName();
                    if ("friendlyName".equals(name)) {
                        d.name = safe(xpp.nextText());
                    } else if ("deviceType".equals(name)) {
                        deviceType = safe(xpp.nextText());
                    } else if ("modelName".equals(name)) {
                        modelName = safe(xpp.nextText());
                    } else if ("UDN".equals(name)) {
                        d.udn = safe(xpp.nextText());
                    } else if ("serviceType".equals(name)) {
                        serviceType = safe(xpp.nextText());
                    } else if ("controlURL".equals(name)) {
                        controlUrl = safe(xpp.nextText());
                        if (AVT_SERVICE.equals(serviceType) && d.controlUrl == null) {
                            d.controlUrl = resolve(location, controlUrl);
                        } else if (REND_SERVICE.equals(serviceType) && d.volumeControlUrl == null) {
                            d.volumeControlUrl = resolve(location, controlUrl);
                        }
                        serviceType = null;
                    } else if ("url".equals(name) && d.iconUrl == null) {
                        d.iconUrl = resolve(location, safe(xpp.nextText()));
                    } else if ("key".equals(name) && (d.key == null || d.key.isEmpty())) {
                        d.key = safe(xpp.nextText());
                    }
                }
                event = xpp.next();
            }

            try {
                URL u = new URL(location);
                d.ip = u.getHost();
                d.port = u.getPort() > 0 ? u.getPort() : 80;
            } catch (Exception ignored) {
            }
            if (d.name == null || d.name.isEmpty()) d.name = d.ip == null ? "Unknown Device" : d.ip;
            boolean basic = deviceType.toLowerCase().contains("basic:1");
            d.dial = basic || (d.controlUrl == null && (location.toLowerCase().endsWith("/dd.xml")
                    || location.toLowerCase().contains("dial")));
            if (d.dial && d.ip != null) {
                d.dialAppUrl = "http://" + d.ip + ":" + d.port + "/apps";
            }
            d.type = classify(modelName, d.name);
            d.etcas = "ETCAS投屏客户端".equals(d.type)
                    || (d.udn != null && d.udn.toLowerCase().contains("etcas"))
                    || (modelName != null && modelName.toLowerCase().contains("etcas"));
            if (d.controlUrl == null && !d.dial) return null;
            return d;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String resolve(String base, String url) {
        try {
            return new URL(new URL(base), url).toString();
        } catch (Exception e) {
            return url;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String classify(String model, String name) {
        String t = (model + " " + name).toLowerCase();
        if (t.contains("etcas")) return "ETCAS投屏客户端";
        if (t.contains("bilibili") || t.contains("哔哩") || t.contains("小电视") || t.contains("云视听")) return "哔哩哔哩 · 云视听小电视";
        if (t.contains("youku") || t.contains("酷喵") || t.contains("cibn") || t.contains("优酷")) return "酷喵 · 优酷 TV";
        if (t.contains("mango") || t.contains("芒果") || t.contains("mgtv")) return "芒果 TV 设备";
        if (t.contains("iqiyi") || t.contains("奇异果") || t.contains("爱奇艺")) return "银河奇异果设备";
        if (t.contains("极光") || t.contains("newtv") || t.contains("未来电视")) return "云视听极光设备";
        if (t.contains("lebo") || t.contains("乐播")) return "乐播投屏设备";
        if (t.contains("hisense") || t.contains("海信")) return "海信智能电视";
        if (t.contains("tcl") || t.contains("雷鸟")) return "TCL 智能电视";
        if (t.contains("sony") || t.contains("索尼")) return "索尼智能电视";
        if (t.contains("samsung") || t.contains("三星")) return "三星智能电视";
        if (t.contains("lg ") || t.contains("乐金")) return "LG 智能电视";
        if (t.contains("philips") || t.contains("飞利浦")) return "飞利浦智能电视";
        if (t.contains("huawei") || t.contains("华为")) return "华为智慧屏";
        if (t.contains("honor") || t.contains("荣耀")) return "荣耀智慧屏";
        if (t.contains("xiaomi") || t.contains("红米") || t.contains("redmi") || t.contains("mibox") || t.contains("小米")) return "小米智能电视";
        if (t.contains("letv") || t.contains("乐视")) return "乐视超级电视";
        if (t.contains("changhong") || t.contains("长虹")) return "长虹智能电视";
        if (t.contains("konka") || t.contains("康佳")) return "康佳智能电视";
        if (t.contains("skyworth") || t.contains("创维")) return "创维智能电视";
        if (t.contains("haier") || t.contains("海尔")) return "海尔智能电视";
        if (t.contains("oppo")) return "OPPO 设备";
        if (t.contains("vivo")) return "vivo 设备";
        if (t.contains("oneplus") || t.contains("一加")) return "一加设备";
        if (t.contains("realme") || t.contains("真我")) return "realme 设备";
        if (t.contains("lenovo") || t.contains("联想")) return "联想设备";
        if (t.contains("amazon") || t.contains("fire tv")) return "Fire TV";
        if (t.contains("chromecast") || t.contains("google tv")) return "Chromecast / Google TV";
        if (t.contains("roku")) return "Roku 设备";
        return "DLNA 投屏设备";
    }
}
