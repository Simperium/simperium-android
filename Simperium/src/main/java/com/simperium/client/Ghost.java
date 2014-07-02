package com.simperium.client;

import org.json.JSONObject;

import java.util.Locale;

public class Ghost implements Diffable {

    private String mKey;
    private Integer mVersion = 0;
    private JSONObject mProperties;

    public Ghost(String key) {
        this(key, 0, new JSONObject());
    }

    public Ghost(String key, Integer version, JSONObject properties) {
        super();
        mKey = key;
        mVersion = version;
        // copy the properties
        mProperties = properties;
    }
    public String getSimperiumKey() {
        return mKey;
    }
    public Integer getVersion() {
        return mVersion;
    }
    public JSONObject getDiffableValue() {
        return mProperties;
    }
    public String getVersionId() {
        return String.format(Locale.US, "%s.%d", mKey, mVersion);
    }
    public String toString() {
        return String.format(Locale.US, "Ghost %s", getVersionId());
    }
}
