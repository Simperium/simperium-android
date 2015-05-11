/**
 * A channel that is always on and always immediately acknowledges changes
 */
package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.Change;
import com.simperium.client.Ghost;
import com.simperium.client.RemoteChange;
import com.simperium.client.RemoteChangeInvalidException;
import com.simperium.client.Syncable;
import com.simperium.util.Uuid;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MockChannel implements Bucket.Channel {

    public static final String TAG = "Simperium.Test";

    private Bucket mBucket;
    private boolean started = false;
    private boolean idle = false;

    public boolean autoAcknowledge = true;

    public MockChannel(Bucket bucket){
        mBucket = bucket;
    }

    @Override
    public Change queueLocalDeletion(Syncable object){
        Change change = new Change(Change.OPERATION_REMOVE, mBucket.getName(), object.getSimperiumKey());
        try {
            if(started && autoAcknowledge) acknowledge(change);
        } catch (Exception e) {
            throw(new RuntimeException(e));
        }
        return change;
    }

    @Override
    public boolean isIdle() {
        return idle;
    }

    @Override
    public Change queueLocalChange(String simperiumKey) {
        Change change = new Change(Change.OPERATION_MODIFY, mBucket.getName(), simperiumKey);
        try {
            if(started && autoAcknowledge) acknowledge(change);
        } catch (Exception e) {
            throw(new RuntimeException(e));
        }
        return change;
    }

    @Override
    public void log(int level, CharSequence message) {
        Log.d(TAG, String.format("Remote log (%d): %s", level, message));
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
    throws Exception {

        Integer sourceVersion = mBucket.getObject(change.getKey()).getVersion();
        Integer entityVersion;
        if (sourceVersion == null) {
            entityVersion = 1;
        } else {
            entityVersion = sourceVersion + 1;
        }
        JSONArray ccids = new JSONArray();
        String cv = Uuid.uuid(0xF);
        ccids.put(change.getChangeId());

        RemoteChange ack;

        if (!change.getOperation().equals(Change.OPERATION_REMOVE)) {
            Ghost ghost = mBucket.getGhost(change.getKey());
            JSONObject object = mBucket.getObject(change.getKey()).getDiffableValue();
            ack = new RemoteChange("fake", change.getKey(), ccids, cv, sourceVersion, entityVersion, change.getDiff(object, ghost));
        } else {
            ack = new RemoteChange("fake", change.getKey(), ccids, cv, sourceVersion, entityVersion, Change.OPERATION_REMOVE, null);
        }

        Log.d(TAG, String.format("Auto acknowledging %s", ack));
        Log.d(TAG, String.format("Patch: %s", ack.getPatch()));
        ack.isAcknowledgedBy(change);
        mBucket.acknowledgeChange(ack, change);
    }
}