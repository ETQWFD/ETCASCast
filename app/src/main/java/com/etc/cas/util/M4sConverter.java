package com.etc.cas.util;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.nio.ByteBuffer;

public class M4sConverter {

    public static boolean isM4s(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.endsWith(".m4s");
    }

    public static File convert(Context ctx, Uri uri, String displayName) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(ctx, uri, null);
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    track = i;
                    break;
                }
            }
            if (track < 0) return null;

            String base = displayName == null ? "clip" : displayName.replaceAll("[^\\w.-]", "_");
            if (base.length() > 40) base = base.substring(base.length() - 40);
            File out = new File(ctx.getCacheDir(), base + "_" + System.currentTimeMillis() + ".mp4");

            MediaFormat fmt = extractor.getTrackFormat(track);
            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(fmt);
            extractor.selectTrack(track);
            muxer.start();

            ByteBuffer buf = ByteBuffer.allocate(1 << 20);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long basePts = -1;
            while (true) {
                int n = extractor.readSampleData(buf, 0);
                if (n < 0) break;
                long pts = extractor.getSampleTime();
                if (basePts < 0) basePts = pts;
                info.offset = 0;
                info.size = n;
                info.presentationTimeUs = pts - basePts;
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(outTrack, buf, info);
                extractor.advance();
            }
            muxer.stop();
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
            try {
                if (muxer != null) muxer.release();
            } catch (Exception ignored) {
            }
        }
    }
}
