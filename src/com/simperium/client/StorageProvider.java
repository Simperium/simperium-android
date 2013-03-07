package com.simperium.client;

import java.util.List;

public interface StorageProvider {
    /**
     * Store bucket object data
     */
    public void addObject(Bucket bucket, String key, Bucket.Syncable object);
    public void updateObject(Bucket bucket, String key, Bucket.Syncable object);
    public void removeObject(Bucket bucket, String key);
    /**
     * Retrieve entities and details
     */
    public <T extends Bucket.Syncable> T getObject(Bucket<T> bucket, String key);
    // public Boolean containsKey(Bucket bucket, String key);
    // public Boolean hasKeyVersion(Bucket bucket, String key, Integer version);
    // public Integer getKeyVersion(Bucket bucket, String key);
    public <T extends Bucket.Syncable> List<T> allEntities(Bucket<T> bucket);
}