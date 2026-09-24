package com.etc.cas.util;

import java.io.Serializable;

public class VideoInfo implements Serializable {
    private static final long serialVersionUID = 2L;

    public String url;
    public String title;
    public String imageUrl;
    public String contentType;
    public boolean directMedia;
    public String referer;
}
