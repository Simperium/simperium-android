package com.simperium.client;

import java.util.List;

public interface StorageProvider {
    String getChangeVersion(Bucket bucket);
    void setChangeVersion(Bucket bucket, String version);
    Boolean hasChangeVersion(Bucket bucket);
    Boolean hasChangeVersion(Bucket bucket, String version);
    void addEntity(Bucket bucket, String key, Bucket.Diffable entity);
    void updateEntity(Bucket bucket, String key, Bucket.Diffable entity);
    <T extends Bucket.Diffable> T getEntity(Bucket<T> bucket, String key);
    Boolean containsKey(Bucket bucket, String key);
    Boolean hasKeyVersion(Bucket bucket, String key, Integer version);
    Integer getKeyVersion(Bucket bucket, String key);
    <T extends Bucket.Diffable> List<T> allEntities(Bucket<T> bucket);
}