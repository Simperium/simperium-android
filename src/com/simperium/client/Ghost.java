package com.simperium.client;

import java.util.Map;
import java.util.HashMap;
/**
 *
 */
public class Ghost implements Diffable {
    private String key;
    private Integer version = 0;
    private Map<String,Object> properties;

    public Ghost(String key){
        this(key, 0, new HashMap<String,Object>());
    }

    public Ghost(String key, Integer version, Map<String,Object> properties){
        super();
        this.key = key;
        this.version = version;
        // copy the properties
        this.properties = Bucket.deepCopy(properties);
    }
    public String getSimperiumKey(){
        return key;
    }
    public Integer getVersion(){
        return version;
    }
    public Map<String,Object> getDiffableValue(){
        return properties;
    }
    public String getVersionId(){
        return String.format("%s.%d", key, version);
    }
}
