package com.simperium.testapp.mock;

import com.simperium.client.Bucket;
import com.simperium.client.Channel.Serializer;
import com.simperium.client.Channel.SerializedQueue;
import com.simperium.client.Change;
import com.simperium.client.Syncable;

public class MockChannelSerializer<T extends Syncable> implements Serializer<T> {

    public int ackCount = 0;

    public SerializedQueue<T> queue = new SerializedQueue<T>();

    /**
     * Return what we want the queue to start with
     */
    @Override
    public SerializedQueue<T> restore(Bucket<T> bucket){
        return queue;
    }

    /**
     * Create a new blank queue
     */
    @Override
    public void reset(Bucket<T> bucket){
        queue = new SerializedQueue<T>();
    }

    public void onQueueChange(Change<T> change){
        android.util.Log.d("Simperium.Test", String.format("Qeueuing %s", change));
        queue.queued.add(change);
    }

    public void onDequeueChange(Change<T> change){
        queue.queued.remove(change);
    }

    public void onSendChange(Change<T> change){
        queue.queued.remove(change);
        queue.pending.put(change.getKey(), change);
    }

    public void onAcknowledgeChange(Change<T> change){
        ackCount ++;
        queue.pending.remove(change.getKey());
    }

}