package com.simperium.android;

import com.simperium.client.Syncable;
import com.simperium.client.Channel;
import com.simperium.client.Change;
import com.simperium.client.Bucket;

import android.database.SQLiteDatabase;

public class QueueSerializer<T extends Syncable> implements Channel.Serializer {

    protected SQLiteDatabase mDatabase;

    public QueueSerializer(SQLiteDatabase database) {
        mDatabase = database;
        prepare();
    }

    private void prepare(){
        // create the table for the database
        // bucket, key, status, version number, operation and diff
        // add indexes for bucket, key and status

    }

    @Override
    public SerializedQueue<T> restore(Bucket<T> bucket) {
        Channel.SerializedQueue queue = new Channel.SerializedQueue();
        // find everything marked as pending or queued and restore the queue
        // pending is a hash with change's simperiumKey as the key and change as value
        // queued is a list of changes that need to be sent
        return queue;
    }

    @Override
    public void reset(Bucket<T> bucket) {
        // delete everything from the queue for this bucket
    }

    @Override
    public void onQueueChange(Change<T> change) {
        // change has been add queued status
    }

    @Override
    public void onDequeueChange(Change<T> change) {
        // change will not be sent, remove queued status
    }

    @Override
    public void onSendChange(Change<T> change) {
        // change was sent, mark as pending
    }

    @Override
    public void onAcknowledgeChange(Change<T> change)} {
        // change was acknowledge, remove pending status
    }

}