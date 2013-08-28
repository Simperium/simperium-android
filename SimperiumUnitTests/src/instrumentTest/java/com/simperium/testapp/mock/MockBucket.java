package com.simperium.testapp.mock;

import com.simperium.client.Bucket;
import com.simperium.client.Bucket.ChannelProvider;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.ObjectCache;
import com.simperium.client.Syncable;
import com.simperium.client.User;

import com.simperium.storage.StorageProvider;
import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.storage.MemoryStore;

public class MockBucket {

    public interface ChannelFactory<T extends Syncable> {
        public ChannelProvider<T> buildChannel(Bucket<T> bucket);
    }


    /**
     * Sets up a bucket instance with the given BucketSchema and provides
     * mock instances of a Bucket's depenencies for testing objects that
     * interface with a bucket.
     */
    public static <T extends Syncable> Bucket<T> buildBucket(BucketSchema<T> schema){
        return buildBucket(schema, new ChannelFactory<T>(){
            @Override
            public ChannelProvider<T> buildChannel(Bucket<T> bucket){
                return new MockChannel<T>(bucket);
            }
        });
    }


    /**
     * Sets up a bucket instance with the provided BucketSchema and configures
     * the ChannelProvider to be used with the bucket.
     */
    public static <T extends Syncable> Bucket<T> buildBucket(BucketSchema<T> schema, ChannelFactory<T> channelFactory){
        User user = MockUser.buildUser();
        StorageProvider storage = new MemoryStore();
        BucketStore<T> store = storage.createStore(schema.getRemoteName(), schema);
        GhostStorageProvider ghosts = new MockGhostStore();
        ObjectCache<T> cache = new ObjectCache<T>(new MockCache<T>());

        Bucket<T> bucket = new Bucket<T>(MockSyncService.service(), schema.getRemoteName(), schema, user, store, ghosts, cache);

        ChannelProvider<T> channel = channelFactory.buildChannel(bucket);
        bucket.setChannel(channel);
        bucket.start();
        return bucket;
    }

}