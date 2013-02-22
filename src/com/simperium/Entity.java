package com.simperium.client;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Used as the "Backing" object or "Ghost" for a bucket entity.
 */
public class Entity extends BucketObject {
    
    protected Map<String,Object> properties = new HashMap<String,Object>();
    
    public static class Factory implements Bucket.EntityFactory<Entity> {
        public Entity buildEntity(String key, Integer version, Map<String,Object> properties){
            return new Entity(key, version, properties);
        }
        public Entity createEntity(String key){
            return new Entity(key);
        }
    }
        
    public Entity(String key){
        setSimperiumId(key);
        setVersion(0);
    }
    
    public Entity(String key, Integer version, JSONObject entityData){
        setSimperiumId(key);
        setVersion(version);
        properties = Entity.convertJSON(entityData);
    }
    
    public Entity(String key, Integer version, Map<String,Object> properties){
        Simperium.log(String.format("Initializing with properties: %s", properties));
        setSimperiumId(key);
        setVersion(version);
        this.properties = properties;
    }
    
    public String toString(){
        return String.format("%s - %s", getBucket().getName(), getSimperiumId());
    }
    
    public Map<String,Object> getDiffableValue(){
        Simperium.log(String.format("Requesting diffable values %s", properties));
        return properties;
    }
    
    public static Entity fromJSON(String key, Integer version, JSONObject entityData){
        Entity entity = new Entity(key, version, entityData);
        return entity;
    }
    
    public boolean equals(Object o){
        if (o == null) {
            return false;
        } else if( o == this){
            return true;    
        } else if(o.getClass() != getClass()){
            return false;
        }
        Entity other = (Entity) o;
        return other.getBucket().equals(getBucket()) && other.getSimperiumId().equals(getSimperiumId());
    }
        
    /**
     * Convert a JSONObject to a HashMap
     */
    public static Map<String,Object> convertJSON(JSONObject json){
        HashMap<String,Object> map = new HashMap<String,Object>(json.length());
        Iterator keys = json.keys();
        while(keys.hasNext()){
            String key = (String)keys.next();
            try {
                Object val = json.get(key);
                // log(String.format("Hello! %s", json.get(key).getClass().getName()));
                if (val.getClass().equals(JSONObject.class)) {
                    map.put(key, convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    map.put(key, convertJSON((JSONArray) val));
                } else {
                    map.put(key, val);
                }
            } catch (JSONException e) {
                Simperium.log(String.format("Error: %s", e.getMessage()), e);
            }
        }
        return map;
        
    }
    
    public static List<Object> convertJSON(JSONArray json){
        ArrayList<Object> list = new ArrayList<Object>(json.length());
        for (int i=0; i<json.length(); i++) {
            try {
                Object val = json.get(i);
                if (val.getClass().equals(JSONObject.class)) {
                    list.add(convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    list.add(convertJSON((JSONArray) val));
                } else {
                    list.add(val);
                }
            } catch (JSONException e) {
                Simperium.log(String.format("Error: %s", e.getMessage()), e);
            }

        }
        return list;
    }
    
}