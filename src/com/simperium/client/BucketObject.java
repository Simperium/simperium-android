package com.simperium.client;

import java.util.Map;
import java.util.HashMap;
/**
 * A generic object used to represent a single object from a bucket
 */
public class BucketObject extends Syncable {
    
    /**
     * Basic implementation of BucketSchema for BucketObject
     */
    public static class Schema implements BucketSchema<BucketObject>{
        private String remoteName;
        public Schema(String remoteName){
            this.remoteName = remoteName;
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
        public BucketObject build(String key, Map<String,Object> properties){
            return new BucketObject(key, properties);
        }
    }

    private Bucket bucket;
    private String simperiumKey;

    protected Map<String,Object> properties;

    public BucketObject(String key, Map<String,Object> properties){
        this.simperiumKey = key;
        this.properties = Bucket.deepCopy(properties);
    }

    public BucketObject(String key){
        this(key, new HashMap<String,Object>());
    }

    public String getSimperiumKey(){
        return simperiumKey;
    }

    public String toString(){
        return String.format("%s - %s", getBucket().getName(), getVersionId());
    }

    public Map<String,Object> getDiffableValue(){
        if (properties == null) {
            properties = new HashMap<String,Object>();
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
