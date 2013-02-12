package com.simperium;

public interface StorageProvider {
    String getChangeVersion(Bucket bucket);
    void setChangeVersion(Bucket bucket, String version);
    Boolean hasChangeVersion(Bucket bucket);
    Boolean hasChangeVersion(Bucket bucket, String version);
    void addEntity(Bucket bucket, String key, Entity entity);
    Entity getEntity(Bucket bucket, String key);
    Boolean containsKey(Bucket bucket, String key);
    Boolean hasKeyVersion(Bucket bucket, String key, Integer version);
    Integer getKeyVersion(Bucket bucket, String key);
}