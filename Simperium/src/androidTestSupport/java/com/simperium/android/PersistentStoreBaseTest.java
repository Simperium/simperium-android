package com.simperium.android;

import android.database.sqlite.SQLiteDatabase;
import android.test.ActivityInstrumentationTestCase2;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.User;

import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.models.Note;

import com.simperium.test.MockChannel;
import com.simperium.test.MockGhostStore;
import com.simperium.test.MockExecutor;

import static com.simperium.TestHelpers.makeUser;

public abstract class PersistentStoreBaseTest extends ActivityInstrumentationTestCase2<AuthenticationActivity> {

    public static final String MASTER_TABLE = "sqlite_master";
    public static final String BUCKET_NAME="bucket";

    protected AuthenticationActivity mActivity;
    
    protected PersistentStore mStore;
    protected BucketStore<Note> mNoteStore;
    protected SQLiteDatabase mDatabase;
    protected String mDatabaseName = "simperium-test-data";
    protected String[] mTableNames = new String[]{"indexes", "objects", "value_caches"};
    protected Bucket<Note> mBucket;
    protected User mUser;
    protected BucketSchema mSchema;
    protected GhostStorageProvider mGhostStore;

    public PersistentStoreBaseTest() {
        super(AuthenticationActivity.class);
    }

    @Override
    protected void setUp()
    throws Exception {

        super.setUp();

        setActivityInitialTouchMode(false);
        mUser = makeUser();
        mActivity = getActivity();
        mDatabase = mActivity.openOrCreateDatabase(mDatabaseName, 0, null);
        mGhostStore = new MockGhostStore();
        mStore = new PersistentStore(mDatabase);
        mSchema = new Note.Schema();
        mNoteStore = mStore.createStore(BUCKET_NAME, mSchema);
        mBucket = new Bucket<Note>(MockExecutor.immediate(), BUCKET_NAME, mSchema, mUser, mNoteStore, mGhostStore);
        Bucket.Channel channel = new MockChannel(mBucket);
        mBucket.setChannel(channel);
        mNoteStore.prepare(mBucket);
    }

    @Override
    protected void tearDown() throws Exception {
        mActivity.deleteDatabase(mDatabaseName);
        super.tearDown();
    }

}