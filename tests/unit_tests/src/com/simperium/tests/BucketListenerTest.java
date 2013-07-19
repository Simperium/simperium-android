package com.simperium.tests;

import junit.framework.TestCase;

import com.simperium.client.Bucket;
import com.simperium.client.Bucket.OnSaveObjectListener;
import com.simperium.client.Bucket.OnDeleteObjectListener;
import com.simperium.client.Bucket.OnNetworkChangeListener;
import com.simperium.client.Syncable;
import com.simperium.client.RemoteChange;
import com.simperium.client.RemoteChangeInvalidException;
import com.simperium.client.Change;
import com.simperium.util.JSONDiff;

import com.simperium.tests.mock.MockBucket;
import com.simperium.tests.models.Note;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class BucketListenerTest extends TestCase {

    private Bucket<Note> mBucket;
    private BucketListener mListener;

    public void setUp() throws Exception {
        super.setUp();
        mBucket = MockBucket.buildBucket(new Note.Schema());

        mListener = new BucketListener();
    }

    public void testOnSaveListener(){
        mBucket.addOnSaveObjectListener(mListener);
        Note note = mBucket.newObject("listener-test");
        note.setTitle("Hola Mundo");
        note.save();

        assertTrue(mListener.saved);
    }

    public void testRemoveOnSaveListener(){
        mBucket.addOnSaveObjectListener(mListener);
        Note note = mBucket.newObject("listener-test");
        note.setTitle("Hola Mundo");
        mBucket.removeOnSaveObjectListener(mListener);
        note.save();

        assertFalse(mListener.saved);
        
    }

    public void testOnDeleteListener(){
        mBucket.addOnDeleteObjectListener(mListener);
        Note note = mBucket.newObject();
        note.setTitle("Hola Mundo");
        note.save();

        assertFalse(mListener.deleted);

        note.delete();

        assertTrue(mListener.deleted);

    }

    public void testUnregsiterOnDeleteListener(){
        mBucket.addOnDeleteObjectListener(mListener);
        Note note = mBucket.newObject();
        note.setTitle("Hola Mundo");
        note.save();

        mBucket.removeOnDeleteObjectListener(mListener);

        note.delete();

        assertFalse(mListener.deleted);
    }

    public void testOnNetworkChangeListener()
    throws RemoteChangeInvalidException {
        mBucket.addOnNetworkChangeListener(mListener);

        // TODO create helper for generating remote changes
        Map<String,Object> diff = new HashMap<String,Object>();
        Map<String,Object> title = new HashMap<String,Object>();
        title.put(JSONDiff.DIFF_OPERATION_KEY, JSONDiff.OPERATION_REPLACE);
        title.put(JSONDiff.DIFF_VALUE_KEY, JSONDiff.OPERATION_REPLACE);
        diff.put("title", title);
        List<String> ccids = new ArrayList<String>();
        ccids.add("fake-ccid");
        RemoteChange change = new RemoteChange("client", "thing", ccids,
            "fake-cv", null, 1, RemoteChange.OPERATION_MODIFY, diff);

        mBucket.applyRemoteChange(change);            
        assertTrue(mListener.changed);
    }

    class BucketListener extends Bucket.Listener<Note> {

        public boolean deleted = false;
        public boolean saved = false;
        public boolean changed = false;

        @Override
        public void onDeleteObject(Bucket<Note> bucket, Note object){
            deleted = true;
        }

        @Override
        public void onSaveObject(Bucket<Note> bucket, Note object){
            saved = true;
        }

        @Override
        public void onChange(Bucket<Note> bucket, Bucket.ChangeType type, String key){
            changed = true;
        }

    }


}