package com.simperium.tests.mock;

import com.simperium.client.Bucket;
import com.simperium.client.Bucket.ChannelProvider;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStoreProvider;
import com.simperium.client.ObjectCache;
import com.simperium.client.Syncable;
import com.simperium.client.User;

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
        User user = MockUser.buildUser();
        StorageProvider storage = new MemoryStore();
        BucketStore<T> store = storage.createStore(schema.getRemoteName(), schema);
        GhostStoreProvider ghosts = new MockGhostStore();
        ObjectCache<T> cache = new ObjectCache<T>(new MockCache<T>());
        
        Bucket<T> bucket = new Bucket<T>(schema.getRemoteName(), schema, user, store, ghosts, cache);
        ChannelProvider<T> channel = new MockChannel<T>(bucket);
        bucket.setChannel(channel);
        bucket.start();
        return bucket;
    }

}