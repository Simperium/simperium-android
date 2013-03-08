package com.simperium.client;

import java.util.List;
import java.util.Map;

public interface StorageProvider {
    /**
     * Store bucket object data
     */
    public void addObject(Bucket<?> bucket, String key, Bucket.Syncable object);
    public void updateObject(Bucket<?> bucket, String key, Bucket.Syncable object);
    public void removeObject(Bucket<?> bucket, String key);
    /**
     * Retrieve entities and details
     */
    public Map<String,Object> getObject(Bucket<?> bucket, String key);
	public List<Map<String,Object>> allObjects(Bucket<?> bucket);
}