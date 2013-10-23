package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.BucketNameInvalid;
import com.simperium.client.BucketSchema;
import com.simperium.client.Change;
import com.simperium.client.ChannelProvider;
import com.simperium.client.Ghost;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.ObjectCacheProvider.ObjectCache;
import com.simperium.client.RemoteChange;
import com.simperium.client.RemoteChangeInvalidException;
import com.simperium.client.SyncService;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.storage.MemoryStore;
import com.simperium.storage.StorageProvider;
import com.simperium.storage.StorageProvider.BucketStore;

public class MockBucket<T extends Syncable> extends Bucket<T> {

    protected RemoteChangeListener mListener;

    public interface RemoteChangeListener {
        public void onApplyRemoteChange(RemoteChange change);
        public void onAcknowledgeRemoteChange(RemoteChange change);
    }

    public MockBucket(SyncService syncService, String name, BucketSchema<T>schema, User user, BucketStore<T> storage, GhostStorageProvider ghostStore, ObjectCache<T> cache)
    throws BucketNameInvalid {
        super(syncService, name, schema, user, storage, ghostStore, cache);
    }

    public void setRemoteChangeListener(RemoteChangeListener listener){
        mListener = listener;
    }

    @Override
    public Ghost applyRemoteChange(RemoteChange change)
    throws RemoteChangeInvalidException {
        if (mListener != null) mListener.onApplyRemoteChange(change);
        return super.applyRemoteChange(change);
    }

    @Override
    public Ghost acknowledgeChange(RemoteChange remoteChange, Change change)
    throws RemoteChangeInvalidException {
        if (mListener != null) mListener.onAcknowledgeRemoteChange(remoteChange);
        return super.acknowledgeChange(remoteChange, change);
    }

    /**
     * Sets up a bucket instance with the given BucketSchema and provides
     * mock instances of a Bucket's depenencies for testing objects that
     * interface with a bucket.
     */
    public static <T extends Syncable> MockBucket<T> buildBucket(BucketSchema<T> schema)
    throws BucketNameInvalid {
        return buildBucket(schema, new MockChannelProvider());
    }


    /**
     * Sets up a bucket instance with the provided BucketSchema and configures
     * the ChannelProvider to be used with the bucket.
     */
    public static <T extends Syncable> MockBucket<T> buildBucket(BucketSchema<T> schema, ChannelProvider provider)
    throws BucketNameInvalid {
        User user = MockUser.buildUser();
        StorageProvider storage = new MemoryStore();
        BucketStore<T> store = storage.createStore(schema.getRemoteName(), schema);
        GhostStorageProvider ghosts = new MockGhostStore();
        MockCache<T> cache = new MockCache<T>();

        MockBucket<T> bucket = new MockBucket<T>(MockSyncService.service(), schema.getRemoteName(), schema, user, store, ghosts, cache);

        Bucket.Channel channel = provider.buildChannel(bucket);
        bucket.setChannel(channel);
        bucket.start();
        return bucket;
    }

}