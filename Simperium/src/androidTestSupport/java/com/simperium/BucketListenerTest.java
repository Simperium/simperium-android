package com.simperium;

import com.simperium.client.Bucket;
import com.simperium.client.RemoteChange;
import com.simperium.models.Note;
import com.simperium.test.MockBucket;
import com.simperium.util.JSONDiff;

import com.simperium.util.RemoteChangesUtil;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONObject;

public class BucketListenerTest extends TestCase {

    private Bucket<Note> mBucket;
    private BucketListener mListener;

    public void setUp() throws Exception {
        super.setUp();
        mBucket = MockBucket.buildBucket(new Note.Schema());

        mListener = new BucketListener();
    }

    public void testOnSaveListener()
    throws Exception {
        mBucket.addOnSaveObjectListener(mListener);
        Note note = mBucket.newObject("listener-test");
        note.setTitle("Hola Mundo");
        note.save();

        assertTrue(mListener.saved);
    }

    public void testRemoveOnSaveListener()
    throws Exception {
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

    public void testUnregsiterOnDeleteListener()
    throws Exception {
        mBucket.addOnDeleteObjectListener(mListener);
        Note note = mBucket.newObject();
        note.setTitle("Hola Mundo");
        note.save();

        mBucket.removeOnDeleteObjectListener(mListener);

        note.delete();

        assertFalse(mListener.deleted);
    }

    public void testOnNetworkChangeListener()
    throws Exception {
        mBucket.addOnNetworkChangeListener(mListener);

        // TODO create helper for generating remote changes
        JSONObject diff = new JSONObject();
        JSONObject title = new JSONObject();
        title.put(JSONDiff.DIFF_OPERATION_KEY, JSONDiff.OPERATION_REPLACE);
        title.put(JSONDiff.DIFF_VALUE_KEY, JSONDiff.OPERATION_REPLACE);
        diff.put("title", title);
        JSONArray ccids = new JSONArray();
        ccids.put("fake-ccid");
        RemoteChange change = new RemoteChange("client", "thing", ccids,
            "fake-cv", null, 1, RemoteChange.OPERATION_MODIFY, diff);

        mBucket.applyRemoteChange(change);
        assertTrue(mListener.changed);
    }

    public void testOnBeforeUpdateListener()
    throws Exception {

        Note note = mBucket.newObject();
        note.setTitle("Hola mundo");
        note.save();

        JSONObject remote = new JSONObject(note.getDiffableValue().toString());
        remote.put("title", "Hello world");

        RemoteChange change = RemoteChangesUtil.buildRemoteChange(note, remote);

        mBucket.addOnBeforeUpdateObjectListener(mListener);

        mBucket.applyRemoteChange(change);

        assertTrue(mListener.beforeUpdate);

    }

    class BucketListener implements Bucket.Listener<Note> {

        public boolean deleted = false;
        public boolean saved = false;
        public boolean changed = false;
        public boolean beforeUpdate = false;

        @Override
        public void onDeleteObject(Bucket<Note> bucket, Note object){
            deleted = true;
        }

        @Override
        public void onSaveObject(Bucket<Note> bucket, Note object){
            saved = true;
        }

        @Override
        public void onNetworkChange(Bucket<Note> bucket, Bucket.ChangeType type, String key){
            changed = true;
        }

        @Override
        public void onBeforeUpdateObject(Bucket<Note> bucket, Note object) {
            beforeUpdate = true;
        }

    }


}