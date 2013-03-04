package com.simperium.client;

import java.util.List;

public interface StorageProvider {
    /**
     * Store the change version for a bucket
     */
    String getChangeVersion(Bucket bucket);
    void setChangeVersion(Bucket bucket, String version);
    Boolean hasChangeVersion(Bucket bucket);
    Boolean hasChangeVersion(Bucket bucket, String version);
    /**
     * Store bucket object data
     */
    void addObject(Bucket bucket, String key, Bucket.Syncable object);
    void updateObject(Bucket bucket, String key, Bucket.Syncable object);
    void removeObject(Bucket bucket, String key);
    /**
     * Retrieve entities and details
     */
    <T extends Bucket.Syncable> T getObject(Bucket<T> bucket, String key);
    Boolean containsKey(Bucket bucket, String key);
    Boolean hasKeyVersion(Bucket bucket, String key, Integer version);
    Integer getKeyVersion(Bucket bucket, String key);
    <T extends Bucket.Syncable> List<T> allEntities(Bucket<T> bucket);
}