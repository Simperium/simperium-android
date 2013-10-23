/**
 * A channel that is always on and always immediately acknowledges changes
 */
package com.simperium.testapp.mock;

import com.simperium.client.Bucket;
import com.simperium.client.Bucket.Channel;
import com.simperium.client.Syncable;
import com.simperium.client.Change;
import com.simperium.client.RemoteChange;
import com.simperium.client.RemoteChangeInvalidException;
import com.simperium.util.Uuid;

import org.json.JSONArray;
import org.json.JSONException;

public class MockChannel implements Bucket.Channel {

    private Bucket mBucket;
    private boolean started = false;

    public boolean autoAcknowledge = true;

    public MockChannel(Bucket bucket){
        mBucket = bucket;
    }

    @Override
    public Change queueLocalDeletion(Syncable object){
        Change change = new Change(Change.OPERATION_REMOVE, object);
        try {
            if(started && autoAcknowledge) acknowledge(change);
        } catch (RemoteChangeInvalidException e) {
            throw( new RuntimeException(e));
        }
        return change;
    }

    @Override
    public Change queueLocalChange(Syncable object) {
        Change change = new Change(Change.OPERATION_MODIFY, object);
        try {
            if(started && autoAcknowledge) acknowledge(change);
        } catch (RemoteChangeInvalidException e) {
            throw(new RuntimeException(e));
        }
        return change;
    }

    @Override
    public void reset(){}

    @Override
    public void start(){
        started = true;
    }

    @Override
    public void stop(){}

    /**
     * Simulate an acknowledged change
     */
    protected void acknowledge(Change change)
    throws RemoteChangeInvalidException {

        Integer sourceVersion = change.getVersion();
        Integer entityVersion = null;
        if (sourceVersion == null) {
            entityVersion = 1;
        } else {
            entityVersion = sourceVersion + 1;
        }
        JSONArray ccids = new JSONArray();
        String cv = Uuid.uuid().substring(0, 0xF);
        ccids.put(change.getChangeId());
        RemoteChange ack;
        try {
            if (!change.getOperation().equals(Change.OPERATION_REMOVE)) {
                ack = new RemoteChange("fake", change.getKey(), ccids, cv, sourceVersion, entityVersion, change.getDiff());
            } else {
                ack = new RemoteChange("fake", change.getKey(), ccids, cv, sourceVersion, entityVersion, Change.OPERATION_REMOVE, null);
            }
        } catch (JSONException e) {
            throw new RemoteChangeInvalidException("Could not make remote chante", e);
        }
        ack.isAcknowledgedBy(change);
        mBucket.acknowledgeChange(ack, change);
    }
}