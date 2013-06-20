package com.simperium.client;

import java.util.Map;
/**
 * The interface all objects must conform to in order to be able to be
 * tracked by Simperium. For a default implementation see BucketObject
 */
public interface Diffable {
    // void setSimperiumId(String id);
    String getSimperiumKey();
    // void setVersion(Integer version);
    Integer getVersion();
    Map<String,Object> getDiffableValue();
}
