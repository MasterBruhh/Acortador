package edu.pucmm.eict.modelos;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Url {
    private ObjectId id;               // ObjectId para MongoDB
    private String originalUrl;
    private String shortUrl;
    private int accessCount;
    private List<Date> accessTimes;
    private List<AccessDetail> accessDetails;
    private Usuario user;

    public Url() {
        // Constructor vacío si hace falta
    }

    public Url(String originalUrl, String shortUrl) {
        this.originalUrl = originalUrl;
        this.shortUrl = shortUrl;
        this.accessCount = 0;
        this.accessTimes = new ArrayList<>();
        this.accessDetails = new ArrayList<>();
    }

    public ObjectId getId() {
        return id;
    }
    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getShortUrl() {
        return shortUrl;
    }
    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public int getAccessCount() {
        return accessCount;
    }
    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public List<Date> getAccessTimes() {
        return accessTimes;
    }
    public List<AccessDetail> getAccessDetails() {
        return accessDetails;
    }

    public void recordAccess(AccessDetail detail) {
        this.accessCount++;
        this.accessTimes.add(detail.getTimestamp());
        this.accessDetails.add(detail);
    }

    public Usuario getUser() {
        return user;
    }
    public void setUser(Usuario user) {
        this.user = user;
    }
}
