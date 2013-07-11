package com.simperium.tests;

import junit.framework.TestCase;

import com.simperium.client.Bucket;
import com.simperium.client.Bucket.OnSaveObjectListener;
import com.simperium.client.Bucket.OnDeleteObjectListener;
import com.simperium.client.Syncable;
import com.simperium.client.Change;

import com.simperium.tests.mock.MockBucket;
import com.simperium.tests.models.Note;

public class BucketListenerTest extends TestCase {

    private Bucket<Note> mBucket;
    private BucketListener<Note> mListener;

    public void setUp() throws Exception {
        super.setUp();
        mBucket = MockBucket.buildBucket(new Note.Schema());

        mListener = new BucketListener<Note>();
    }

    public void testOnSaveListener(){
        mBucket.registerOnSaveObjectListener(mListener);
        Note note = mBucket.newObject("listener-test");
        note.setTitle("Hola Mundo");
        note.save();

        assertTrue(mListener.saved);
    }

    public void testUnregisterOnSaveListener(){
        mBucket.registerOnSaveObjectListener(mListener);
        Note note = mBucket.newObject("listener-test");
        note.setTitle("Hola Mundo");
        mBucket.unregisterOnSaveObjectListener(mListener);
        note.save();

        assertFalse(mListener.saved);
        
    }

    public void testOnDeleteListener(){
        mBucket.registerOnDeleteObjectListener(mListener);
        Note note = mBucket.newObject();
        note.setTitle("Hola Mundo");
        note.save();

        assertFalse(mListener.deleted);

        note.delete();

        assertTrue(mListener.deleted);

    }

    public void testUnregsiterOnDeleteListener(){
        mBucket.registerOnDeleteObjectListener(mListener);
        Note note = mBucket.newObject();
        note.setTitle("Hola Mundo");
        note.save();

        mBucket.unregisterOnDeleteObjectListener(mListener);

        note.delete();

        assertFalse(mListener.deleted);
    }

    class BucketListener<T extends Syncable>
    implements OnDeleteObjectListener<T>,
    OnSaveObjectListener<T> {

        public boolean deleted = false;
        public boolean saved = false;

        @Override
        public void onDeleteObject(T object){
            deleted = true;
        }

        @Override
        public void onSaveObject(T object){
            saved = true;
        }

    }


}