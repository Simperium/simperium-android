package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.BucketSchema;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.Query;
import com.simperium.client.Syncable;

import java.util.List;

public interface StorageProvider {
    /**
     * Store and query bucket object data
     */
    public interface BucketStore<T extends Syncable> {

        /**
         * Add/Update the given object
         */
        public void save(T object, List<Index> indexes);

        /**
         * Remove the given object from the storage
         */
        public void delete(T object);

        /**
         * Delete all objects from storage
         */
        public void reset();

        /**
         * Get an object with the given key
         */
        public T get(String key) throws BucketObjectMissingException;

        /**
         * All objects, returns a cursor for the given bucket
         */
        public Bucket.ObjectCursor<T> all();

        /**
         * 
         */
        public Bucket.ObjectCursor<T> search(Query<T> query);

        /**
         * Return the count for the given query
         */
        public int count(Query<T> query);

    }

    /**
     * 
     */
    public <T extends Syncable> BucketStore<T> createStore(String bucketName, BucketSchema<T> bucketSchema);
}