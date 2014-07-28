package com.simperium.android;

import com.simperium.client.BucketNameInvalid;
import com.simperium.client.BucketSchema;
import com.simperium.client.Ghost;
import com.simperium.client.GhostMissingException;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.Query;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.storage.StorageProvider.BucketStore;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.Executor;

public class Bucket<T extends Syncable> extends com.simperium.client.Bucket<T> {

    public interface ObjectCursor<T extends Syncable> extends com.simperium.client.Bucket.ObjectCursor<T>, Cursor {
 
    }

    private class BucketCursor extends CursorWrapper implements ObjectCursor<T> {

        private ObjectCursor<T> cursor;

        BucketCursor(ObjectCursor<T> cursor) {
            super(cursor);
            this.cursor = cursor;
        }

        @Override
        public String getSimperiumKey() {
            return cursor.getSimperiumKey();
        }

        @Override
        public T getObject() {
            String key = getSimperiumKey();

            T object = cursor.getObject();
            try {
                Ghost ghost = mGhostStore.getGhost(Bucket.this, key);
                object.setGhost(ghost);
            } catch (GhostMissingException e) {
                object.setGhost(new Ghost(key, 0, new JSONObject()));
            }
            object.setBucket(Bucket.this);
            return object;
        }

    }

    final protected StorageProvider.BucketStore<T> mStorage;
    final protected GhostStorageProvider mGhostStore;

    public Bucket(Executor executor, String name, BucketSchema<T>schema, User user,
        StorageProvider.BucketStore<T> storage, GhostStorageProvider ghostStore)
    throws BucketNameInvalid {
        super(executor, name, schema, user, storage, ghostStore);
        mStorage = storage;
        mGhostStore = ghostStore;
    }

    /**
     * Find all objects
     */
    @Override
    public ObjectCursor<T> allObjects() {
        return new BucketCursor(mStorage.all());
    }

    /**
     * Search using a query
     */
    @Override
    public ObjectCursor<T> searchObjects(Query<T> query) {
        return new BucketCursor(mStorage.search(query));
    }

}