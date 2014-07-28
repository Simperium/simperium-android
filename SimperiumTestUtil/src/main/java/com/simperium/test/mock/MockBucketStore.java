package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.Query;
import com.simperium.client.Syncable;
import com.simperium.storage.StorageProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockBucketStore<T extends Syncable> implements StorageProvider.BucketStore<T> {

    static public final String TAG = "Simperium.MockBucketStore";

    private Map<String, T> objects = Collections.synchronizedMap(new HashMap<String, T>(32));

    /**
     * Add/Update the given object
     */
    @Override
    public void save(T object, List<Index> indexes) {
        objects.put(object.getSimperiumKey(), object);
    }

    /**
     * Remove the given object from the storage
     */
    @Override
    public void delete(T object) {
        objects.remove(object.getSimperiumKey());
    }

    /**
     * Delete all objects from storage
     */
    @Override
    public void reset() {
        objects.clear();
    }

    /**
     * Get an object with the given key
     */
    @Override
    public T get(String key) {
        return objects.get(key);
    }

    /**
     * Get a cursor to all the objects
     */
    @Override
    public Bucket.ObjectCursor<T> all() {
        return new MemoryCursor();
    }

    /**
     * Search
     */
    @Override
    public Bucket.ObjectCursor<T> search(Query query) {
        return all();
    }

    /**
     * Count
     */
    @Override
    public int count(Query query) {
        return objects.size();
    }

    private class MemoryCursor
    implements Bucket.ObjectCursor {

        String[] columns = new String[]{"simperiumKey", "object"};
        List<T> objects;
        private int mPosition = 0;

        MemoryCursor() {
            objects = new ArrayList<T>(MockBucketStore.this.objects.values());
        }

        public int getPosition() {
            return mPosition;
        }

        /**
         * Return the current item's siperium key
         */
        @Override
        public String getSimperiumKey() {
            return getObject().getSimperiumKey();
        }

        /**
         * Return the object for the current index in the cursor
         */
        @Override
        public T getObject() {
            return objects.get(getPosition());
        }

        public boolean moveToNext() {
            if (mPosition + 1 >= objects.size()) {
                return false;
            }

            mPosition ++;

            return true;
        }

    }

}