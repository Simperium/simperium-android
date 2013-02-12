package com.simperium;
import org.json.JSONObject;
/**
 * Used as the "Backing" object or "Ghost" for a bucket entity.
 */
public class Entity extends BucketObject {
    
    private JSONObject entityData;
    
    public Entity(String key, Integer version, JSONObject entityData){
        setSimperiumId(key);
        setVersion(version);
    }
    
    public static Entity fromJSON(String key, Integer version, JSONObject entityData){
        Entity entity = new Entity(key, version, entityData);
        return entity;
    }
    
}