package com.simperium.testapp;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;

import android.database.sqlite.SQLiteDatabase;

import com.simperium.client.Bucket;
import com.simperium.client.Bucket.ObjectCursor;
import com.simperium.client.Query;
import com.simperium.client.Syncable;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.User;
import com.simperium.content.ObjectCursorLoader;
import com.simperium.storage.PersistentStore;
import com.simperium.storage.StorageProvider.BucketStore;

import com.simperium.util.Uuid;

import com.simperium.testapp.models.Note;
import com.simperium.testapp.mock.MockGhostStore;

import android.app.LoaderManager;
import android.os.Bundle;
import android.content.Loader;

public class ObjectCursorLoaderTest extends ActivityInstrumentationTestCase2<ListActivity> {

    public static final String TAG = MainActivity.TAG;
    public static final String MASTER_TABLE = "sqlite_master";

    private ListActivity mActivity;
    private Bucket<Note> mBucket;
    private SQLiteDatabase mDatabase;
    private String mDatabaseName = "test-db";

    public ObjectCursorLoaderTest() {
        super("com.simperium.tests", ListActivity.class);
    }

    @Override
    protected void setUp() throws Exception {

        super.setUp();

        setActivityInitialTouchMode(false);
        mActivity = getActivity();
        mDatabase = mActivity.openOrCreateDatabase(mDatabaseName, 0, null);
        PersistentStore db = new PersistentStore(mDatabase);
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = db.createStore("notes", schema);
        mBucket = new Bucket("notes", schema, makeUser(), store, new MockGhostStore());

    }

    @Override
    protected void tearDown() throws Exception {
        mActivity.deleteDatabase(mDatabaseName);
        super.tearDown();
    }

    public void testSetup(){
        LoaderManager manager = mActivity.getLoaderManager();
        manager.initLoader(0x0, null, new LoaderCallbacks());
        assertTrue(true);
    }

    private class LoaderCallbacks implements LoaderManager.LoaderCallbacks<ObjectCursor<Note>> {

        @Override
        public Loader<ObjectCursor<Note>> onCreateLoader(int loaderId, Bundle bundle) {
            Query<Note> query = mBucket.query();
            return new ObjectCursorLoader(mActivity, query);
        }

        @Override
        public void onLoadFinished(Loader<ObjectCursor<Note>> loader, ObjectCursor<Note> data){
            
        }

        @Override
        public void onLoaderReset(Loader<ObjectCursor<Note>> loader){
            
        }
    }

    static protected User makeUser(String email, String token){
        User user = new User();
        user.setEmail(email);
        user.setAccessToken(token);
        return user;
    }

    static protected User makeUser(String email){
        return makeUser("test@example.com");
    }

    static protected User makeUser(){
        return makeUser("test@example.com", "fake-token");
    }

}
