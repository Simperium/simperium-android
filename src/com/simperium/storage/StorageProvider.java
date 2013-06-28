package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.Syncable;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Query;

import android.database.Cursor;

import java.util.List;
import java.util.Map;

public interface StorageProvider {
    /**
     * Store and query bucket object data
     */
    public interface BucketStore<T extends Syncable> {
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
        abstract public T get(String key) throws BucketObjectMissingException;
        /**
         * All objects, returns a cursor for the given bucket
         */
        abstract public Bucket.ObjectCursor<T> all();
        /**
         * 
         */
        abstract public Bucket.ObjectCursor<T> search(Query<T> query);
    }
    /**
     * 
     */
    public <T extends Syncable> BucketStore<T> createStore(String bucketName, BucketSchema<T> bucketSchema);
}