package com.simperium.testapp.mock;

import com.simperium.client.Bucket;
import com.simperium.client.Bucket.Channel;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.client.ChannelProvider;

import com.simperium.storage.StorageProvider;
import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.storage.MemoryStore;

public class MockBucket {

    /**
     * Sets up a bucket instance with the given BucketSchema and provides
     * mock instances of a Bucket's depenencies for testing objects that
     * interface with a bucket.
     */
    public static <T extends Syncable> Bucket<T> buildBucket(BucketSchema<T> schema){
        return buildBucket(schema, new MockChannelProvider());
    }


    /**
     * Sets up a bucket instance with the provided BucketSchema and configures
     * the ChannelProvider to be used with the bucket.
     */
    public static <T extends Syncable> Bucket<T> buildBucket(BucketSchema<T> schema, ChannelProvider provider){
        User user = MockUser.buildUser();
        StorageProvider storage = new MemoryStore();
        BucketStore<T> store = storage.createStore(schema.getRemoteName(), schema);
        GhostStorageProvider ghosts = new MockGhostStore();
        MockCache<T> cache = new MockCache<T>();

        Bucket<T> bucket = new Bucket<T>(MockSyncService.service(), schema.getRemoteName(), schema, user, store, ghosts, cache);

        Bucket.Channel channel = provider.buildChannel(bucket);
        bucket.setChannel(channel);
        bucket.start();
        return bucket;
    }

}