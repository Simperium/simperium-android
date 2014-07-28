package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.Change;
import com.simperium.client.Channel.SerializedQueue;
import com.simperium.client.Channel.Serializer;

public class MockChannelSerializer implements Serializer {

    public int ackCount = 0;

    public SerializedQueue queue = new SerializedQueue();

    /**
     * Return what we want the queue to start with
     */
    @Override
    public SerializedQueue restore(Bucket bucket){
        return queue;
    }

    /**
     * Create a new blank queue
     */
    @Override
    public void reset(Bucket bucket){
        queue = new SerializedQueue();
    }

    public void onQueueChange(Change change){
        queue.queued.add(change);
    }

    public void onDequeueChange(Change change){
        queue.queued.remove(change);
    }

    public void onSendChange(Change change){
        queue.queued.remove(change);
        queue.pending.put(change.getKey(), change);
    }

    public void onAcknowledgeChange(Change change){
        ackCount ++;
        queue.pending.remove(change.getKey());
    }

}