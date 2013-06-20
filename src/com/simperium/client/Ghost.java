package com.simperium.client.bucket;

import java.util.Map;
/**
 *
 */
public class Ghost implements Diffable {
    private String key;
    private Integer version = 0;
    private Map<String,Object> properties;

    public Ghost(String key, Integer version, Map<String,Object> properties){
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
