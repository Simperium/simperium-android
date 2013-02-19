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
    
    private Map<String,Object> properties;
    
    public Entity(String key){
        setSimperiumId(key);
        setVersion(0);
        this.properties = new HashMap<String,Object>();
    }
    
    public Entity(String key, Integer version, JSONObject entityData){
        setSimperiumId(key);
        setVersion(version);
        properties = Entity.convertJSON(entityData);
    }
    
    public Entity(String key, Integer version, Map<String,Object> properties){
        setSimperiumId(key);
        setVersion(version);
        this.properties = properties;
    }
    
    public Map<String,Object> getDiffableValue(){
        return properties;
    }
    
    public static Entity fromJSON(String key, Integer version, JSONObject entityData){
        Entity entity = new Entity(key, version, entityData);
        return entity;
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