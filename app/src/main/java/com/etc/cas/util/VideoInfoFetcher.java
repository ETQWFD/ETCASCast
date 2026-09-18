package com.etc.cas.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoInfoFetcher {

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OG_IMAGE = Pattern.compile("property=\"og:image\"[^>]*content=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE2 = Pattern.compile("name=\"og:image\"[^>]*content=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_SRC = Pattern.compile("<video[^>]+src=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE);

    private static final String[] MEDIA_EXT = {".mp4", ".m3u8", ".mkv", ".webm", ".mov", ".avi", ".ts", ".flv", ".mp3", ".flac", ".wav", ".aac", ".ogg", ".jpg", ".jpeg", ".png", ".gif", ".webp"};

    public static VideoInfo fetch(String url) {
        VideoInfo info = new VideoInfo();
        info.url = url;
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) return null;

            String ct = conn.getContentType();
            info.contentType = ct == null ? "" : ct.toLowerCase();
            String finalUrl = conn.getURL() != null ? conn.getURL().toString() : url;
            info.url = finalUrl;

            if (info.contentType.startsWith("video/") || info.contentType.startsWith("audio/")
                    || info.contentType.startsWith("image/") || isMediaExt(finalUrl)) {
                info.directMedia = true;
                info.title = titleFromUrl(finalUrl);
                return info;
            }

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0 && total < 512 * 1024) {
                bos.write(buf, 0, n);
                total += n;
            }
            in.close();
            String html = bos.toString("UTF-8");

            Matcher m = TITLE.matcher(html);
            if (m.find()) {
                info.title = m.group(1).trim().replaceAll("\\s+", " ");
                if (info.title.length() > 120) info.title = info.title.substring(0, 120);
            } else {
                info.title = titleFromUrl(finalUrl);
            }

            Matcher im = OG_IMAGE.matcher(html);
            if (!im.find()) im = OG_IMAGE2.matcher(html);
            if (im.find()) info.imageUrl = im.group(1).trim();

            Matcher vm = VIDEO_SRC.matcher(html);
            if (vm.find()) {
                String vs = vm.group(1).trim();
                if (vs.startsWith("//")) vs = "https:" + vs;
                else if (vs.startsWith("/")) {
                    URL base = new URL(finalUrl);
                    vs = base.getProtocol() + "://" + base.getHost() + vs;
                }
                info.directMedia = true;
                info.url = vs;
            }
            return info;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean isMediaExt(String url) {
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        for (String ext : MEDIA_EXT) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static String titleFromUrl(String url) {
        try {
            URL u = new URL(url);
            String path = u.getPath();
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            return name.isEmpty() ? url : name;
        } catch (Exception e) {
            return url;
        }
    }
}
