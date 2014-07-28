package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.BucketNameInvalid;
import com.simperium.client.BucketSchema;
import com.simperium.client.Change;
import com.simperium.client.ChannelProvider;
import com.simperium.client.Ghost;
import com.simperium.client.Query;
import com.simperium.client.RemoteChange;
import com.simperium.client.RemoteChangeInvalidException;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.storage.StorageProvider.BucketStore;

import java.util.concurrent.Executor;

public class MockBucket<T extends Syncable> extends Bucket<T> {

    protected RemoteChangeListener mListener;
    public MockGhostStore ghostStore;
    public BucketStore<T> storage;

    public interface RemoteChangeListener {
        public void onApplyRemoteChange(RemoteChange change);
        public void onAcknowledgeRemoteChange(RemoteChange change);
    }

    public MockBucket(Executor executor, String name, BucketSchema<T>schema, User user, BucketStore<T> storage, MockGhostStore ghostStore)
    throws BucketNameInvalid {
        super(executor, name, schema, user, storage, ghostStore);
        this.ghostStore = ghostStore;
        this.storage = storage;
    }

    /**
     * Find all objects
     */
    @Override
    public ObjectCursor<T> allObjects() {
        return storage.all();
    }

    /**
     * Search using a query
     */
    @Override
    public ObjectCursor<T> searchObjects(Query<T> query) {
        return storage.all();
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
        return buildBucket(MockExecutor.immediate(), schema, provider);
    }

    /**
     * Build a mock bucket with the given Executor, Schema and ChannelProvider
     */
    public static <T extends Syncable> MockBucket<T> buildBucket(Executor executor, BucketSchema<T> schema, ChannelProvider provider)
    throws BucketNameInvalid {
        User user = MockUser.buildUser();
        BucketStore<T> store = new MockBucketStore<T>();
        MockGhostStore ghosts = new MockGhostStore();

        MockBucket<T> bucket = new MockBucket<T>(executor, schema.getRemoteName(), schema, user, store, ghosts);

        Bucket.Channel channel = provider.buildChannel(bucket);
        bucket.setChannel(channel);
        bucket.start();
        return bucket;
    }

}