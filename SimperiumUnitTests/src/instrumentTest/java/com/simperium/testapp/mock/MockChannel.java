/**
 * A channel that is always on and always immediately acknowledges changes
 */
package com.simperium.testapp.mock;

import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider;
import com.simperium.client.Syncable;
import com.simperium.client.Change;
import com.simperium.client.RemoteChange;
import com.simperium.client.RemoteChangeInvalidException;
import com.simperium.util.Uuid;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class MockChannel<T extends Syncable> implements ChannelProvider<T> {

    private Bucket<T> mBucket;
    private boolean started = false;

    public MockChannel(Bucket<T> bucket){
        mBucket = bucket;
    }

    @Override
    public Change<T> queueLocalDeletion(T object){
        Change<T> change = new Change<T>(Change.OPERATION_REMOVE, object);
        try {
            if(started) acknowledge(change);
        } catch (RemoteChangeInvalidException e) {
            throw( new RuntimeException(e));
        }
        return change;
    }

    @Override
    public Change<T> queueLocalChange(T object) {
        Change<T> change = new Change<T>(Change.OPERATION_MODIFY, object);
        try {
            if(started) acknowledge(change);
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
    public boolean isIdle(){
        return true;
    }

    @Override
    public ChannelProvider.RevisionsRequest getRevisions(String key, int sinceVersion, ChannelProvider.RevisionsRequestCallbacks callbacks){
        for(int i=1;i<sinceVersion;i++){
            Map<String,Object> map = new HashMap<String,Object>();
            map.put("title", String.format("Title %d", i));
            map.put("content", String.format("Content %d", i));
            callbacks.onRevision(key, i, map);
        }
        callbacks.onComplete();
        // mock history request that is always complete
        return new ChannelProvider.RevisionsRequest(){
            @Override
            public boolean isComplete(){
                return true;
            }
        };
    }

    /**
     * Simulate an acknowledged change
     */
    protected void acknowledge(Change<T> change)
    throws RemoteChangeInvalidException {

        Integer sourceVersion = change.getVersion();
        Integer entityVersion = null;
        if (sourceVersion == null) {
            entityVersion = 1;
        } else {
            entityVersion = sourceVersion + 1;
        }
        List<String> ccids = new ArrayList<String>(1);
        String cv = Uuid.uuid().substring(0, 0xF);
        ccids.add(change.getChangeId());
        RemoteChange ack;
        if (!change.getOperation().equals(Change.OPERATION_REMOVE)) {
            ack = new RemoteChange("fake", change.getKey(), ccids, cv, sourceVersion, entityVersion, change.getDiff());
        } else {
            ack = new RemoteChange("fake", change.getKey(), ccids, cv, sourceVersion, entityVersion, Change.OPERATION_REMOVE, null);
        }
        ack.isAcknowledgedBy(change);
        mBucket.acknowledgeChange(ack, change);
    }

}