package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.Syncable;

import java.util.List;
import java.util.Map;

public interface StorageProvider {
    /**
     * Store bucket object data
     */
    public void addObject(Bucket<?> bucket, String key, Syncable object);
    public void updateObject(Bucket<?> bucket, String key, Syncable object);
    public void removeObject(Bucket<?> bucket, String key);
    public void resetBucket(Bucket<?> bucket);
    /**
     * Retrieve entities and details
     */
    public Map<String,Object> getObject(Bucket<?> bucket, String key);
    public List<String> allKeys(Bucket<?> bucket);
}