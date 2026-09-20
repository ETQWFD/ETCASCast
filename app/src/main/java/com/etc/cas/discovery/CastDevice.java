package com.etc.cas.discovery;

import java.io.Serializable;

public class CastDevice implements Serializable {
    private static final long serialVersionUID = 2L;

    public String name;
    public String type;
    public String ip;
    public int port;
    public String location;
    public String udn;
    public String controlUrl;
    public String volumeControlUrl;
    public String iconUrl;
    public boolean dial;
    public String dialAppUrl;
    public boolean etcas;
    public String key;

    public String baseUrl() {
        int p = port > 0 ? port : 80;
        return "http://" + ip + ":" + p;
    }

    public String getInfoLine() {
        return type + " · " + ip;
    }

    @Override
    public String toString() {
        return name;
    }
}
