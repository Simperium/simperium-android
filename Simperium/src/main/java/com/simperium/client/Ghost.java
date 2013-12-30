package com.simperium.client;

import org.json.JSONObject;

import java.util.Locale;

public class Ghost implements Diffable {

    private String key;
    private Integer version = 0;
    private JSONObject properties;

    public Ghost(String key){
        this(key, 0, new JSONObject());
    }

    public Ghost(String key, Integer version, JSONObject properties){
        super();
        this.key = key;
        this.version = version;
        // copy the properties
        this.properties = properties;
    }
    public String getSimperiumKey(){
        return key;
    }
    public Integer getVersion(){
        return version;
    }
    public JSONObject getDiffableValue(){
        return properties;
    }
    public String getVersionId(){
        return String.format(Locale.US, "%s.%d", key, version);
    }
    public String toString(){
        return String.format(Locale.US, "Ghost %s", getVersionId());
    }
}
