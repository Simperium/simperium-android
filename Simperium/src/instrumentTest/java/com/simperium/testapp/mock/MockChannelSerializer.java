package com.simperium.testapp.mock;

import com.simperium.client.Bucket;
import com.simperium.client.Channel.Serializer;
import com.simperium.client.Channel.SerializedQueue;
import com.simperium.client.Syncable;

public class MockChannelSerializer<T extends Syncable> implements Serializer {

    @Override
    public <T extends Syncable> void save(Bucket<T> bucket, SerializedQueue<T> data){
    }

    @Override
    public <T extends Syncable> SerializedQueue<T> restore(Bucket<T> bucket){
        return new SerializedQueue<T>();
    }

    @Override
    public <T extends Syncable> void reset(Bucket<T> bucket){
    }

}