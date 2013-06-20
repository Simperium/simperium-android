package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.Syncable;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Naive implementation of a StorageProvider in memory.
 */
public class MemoryStore implements StorageProvider {

    private Object lock = new Object();
    private Map<Bucket,Map<String,Map<String,Object>>> buckets = new HashMap<Bucket,Map<String,Map<String,Object>>>();

    private Map<String,Map<String,Object>> getBucketList(Bucket<?> bucket){
        if (!buckets.containsKey(bucket)) {
            buckets.put(bucket, new HashMap<String, Map<String,Object>>());
        }
        return buckets.get(bucket);
    }
    /**
     * Store bucket object data
     */
    @Override
    public void addObject(Bucket<?> bucket, String key, Syncable object){
        getBucketList(bucket).put(key, object.getDiffableValue());
    }
    @Override
    public void updateObject(Bucket<?> bucket, String key, Syncable object){
        getBucketList(bucket).put(key, object.getDiffableValue());        
    }
    @Override
    public void removeObject(Bucket<?> bucket, String key){
        getBucketList(bucket).remove(key);
    }
    @Override
    public void resetBucket(Bucket<?> bucket){
        buckets.remove(bucket);
    }
    /**
     * Retrieve entities and details
     */
    @Override
    public Map<String,Object> getObject(Bucket<?> bucket, String key){
        return getBucketList(bucket).get(key);
    }
    @Override
    public List<Map<String,Object>> allObjects(Bucket<?> bucket){
        return new ArrayList(getBucketList(bucket).values());
    }
}