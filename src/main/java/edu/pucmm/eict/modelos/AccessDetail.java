package edu.pucmm.eict.modelos;

import java.util.Date;

public class AccessDetail {
    private Date timestamp;
    private String browser;
    private String ip;
    private String clientDomain;
    private String platform;

    public AccessDetail(Date timestamp, String browser, String ip, String clientDomain, String platform) {
        this.timestamp = timestamp;
        this.browser = browser;
        this.ip = ip;
        this.clientDomain = clientDomain;
        this.platform = platform;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getBrowser() {
        return browser;
    }

    public String getIp() {
        return ip;
    }

    public String getClientDomain() {
        return clientDomain;
    }

    public String getPlatform() {
        return platform;
    }
}
