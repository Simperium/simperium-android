package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.Syncable;

import android.database.Cursor;

import java.util.List;
import java.util.Map;

public interface StorageProvider {
    /**
     * Cursor for bucket data
     */
    public interface BucketCursor<T extends Syncable> extends Cursor {
        /**
         * Return the object for the current index in the cursor
         */
        public T getObject();
    }
    /**
     * Store and query bucket object data
     */
    public static abstract class BucketStore<T extends Syncable> {
        private BucketSchema<T> schema;
        public BucketStore(BucketSchema<T> schema){
            this.schema = schema;
        }
        public BucketSchema<T> getSchema(){
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
         * Delete all objects from storage
         */
        abstract public void reset();
        /**
         * Get an object with the given key
         */
        abstract public T get(String key);
        /**
         * All objects, returns a cursor for the given bucket
         */
        abstract public BucketCursor<T> all();
    }
    /**
     * 
     */
    public <T extends Syncable> BucketStore<T> createStore(BucketSchema<T> bucket);
}