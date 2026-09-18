package com.etc.cas.discovery;

import java.io.Serializable;

public class CastDevice implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String type;
    public String ip;
    public int port;
    public String location;
    public String udn;
    public String controlUrl;
    public String volumeControlUrl;
    public String iconUrl;

    public String getInfoLine() {
        return type + " · " + ip;
    }

    @Override
    public String toString() {
        return name;
    }
}
