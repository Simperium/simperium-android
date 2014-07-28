package com.simperium.android;

import com.simperium.client.BucketSchema;
import com.simperium.client.Query;
import com.simperium.client.Syncable;
    
public interface StorageProvider extends com.simperium.storage.StorageProvider {

    public interface BucketStore<T extends Syncable> extends com.simperium.storage.StorageProvider.BucketStore<T> {

        public void prepare(Bucket<T> bucket);

        @Override
        public Bucket.ObjectCursor<T> all();
        @Override
        public Bucket.ObjectCursor<T> search(Query<T> query);

    }

    public <T extends Syncable> BucketStore<T> createStore(String bucketName, BucketSchema<T> bucketSchema);
}