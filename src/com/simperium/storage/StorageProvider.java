package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.Syncable;

import java.util.List;
import java.util.Map;

public interface StorageProvider {
    /**
     * Store and query bucket object data
     */
    public static abstract BucketStore<T extends Syncable> {
        private BucketSchema<T> schema;
        public BucketStore(BucketSchema<T> schema){
            this.schema = schema;
        }
        public getSchema(){
            return schema;
        }
        /**
         * Add/Update the given object
         */
        abstract public void save(T object);
        /**
         * Remove the given object from the storage
         */
        abstract public void delete(T object);
        /**
         * Remove the object identified by the given key from storage
         */
        abstract public void delete(String key);
        /**
         * Delete all objects from storage
         */
        abstract public void reset();
        /**
         * Get an object with the given key
         */
        abstract public T get(String key);
    }
    /**
     * Retrieve entities and details
     */
    public Map<String,Object> getObject(Bucket<?> bucket, String key);
    public List<String> allKeys(Bucket<?> bucket);
    /**
     * 
     */
    public <T extends Syncable> BucketStore<T> createStore(BucketSchema<T> bucket);
}