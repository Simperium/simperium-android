package com.simperium.client;

import com.simperium.util.JSONDiff;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A generic object used to represent a single object from a bucket
 */
public class BucketObject extends Syncable {

    static public final String TAG = "Simperium";

    /**
     * Basic implementation of BucketSchema for BucketObject
     */
    public static class Schema extends BucketSchema<BucketObject>{
        private String remoteName;

        public Schema(){
            this(null);
        }

        public Schema(String name){
            this.remoteName = name;
        }

        public String getRemoteName(){
            return remoteName;
        }

        public String getRemoteName(Bucket bucket){
            if (remoteName == null) {
                return bucket.getName();
            }
            return remoteName;
        }

        public BucketObject build(String key, JSONObject properties){
            return new BucketObject(key, properties);
        }

        public void update(BucketObject object, JSONObject properties){
            object.properties = properties;
        }

    }

    private String simperiumKey;

    protected JSONObject properties;

    public BucketObject(String key, JSONObject properties){
        this.simperiumKey = key;
        this.properties = JSONDiff.deepCopy(properties);
    }

    public BucketObject(String key){
        this(key, new JSONObject());
    }

    public Object getProperty(String key){
        return properties.opt(key);
    }

    public void setProperty(String key, Object value){
        try {
            properties.put(key, value);
        } catch (JSONException e) {
            android.util.Log.e(TAG, "Could not set key" + key, e);
        }
    }

    public String getSimperiumKey(){
        return simperiumKey;
    }

    public String toString(){
        if (getBucket() == null) {
            return String.format("<no bucket> - %s", getVersionId());
        }
        return String.format("%s - %s", getBucket().getName(), getVersionId());
    }

    public JSONObject getDiffableValue(){
        if (properties == null) {
            properties = new JSONObject();
        }
        return properties;
    }

    public boolean equals(Object o){
        if (o == null) {
            return false;
        } else if( o == this){
            return true;
        } else if(o.getClass() != getClass()){
            return false;
        }
        BucketObject other = (BucketObject) o;
        return other.getBucket().equals(getBucket()) && other.getSimperiumKey().equals(getSimperiumKey());
    }

}
