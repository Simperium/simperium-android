package com.simperium.tests;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStoreProvider;
import com.simperium.client.User;
import com.simperium.storage.MemoryStore;

import com.simperium.tests.models.Note;
/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.simperium.tests.BucketTest \
 * com.simperium.tests.unit/android.test.InstrumentationTestRunner
 */
public class BucketTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private Bucket<Note> mBucket;
    private BucketSchema<Note> mSchema;
    private User mUser;
    private GhostStoreProvider mGhostStore;

    private static String BUCKET_NAME="local-notes";

    public BucketTest() {
        super("com.simperium.tests", MainActivity.class);
    }

    protected void setUp() throws Exception {
        super.setUp();

        mUser = new User(new User.AuthenticationListener(){
            @Override
            public void onAuthenticationStatusChange(User.AuthenticationStatus status){
                // noop
            }
        });

        mSchema = new Note.Schema();
        MemoryStore storage = new MemoryStore();
        mGhostStore = new MemoryGhostStore();
        mBucket = new Bucket<Note>(BUCKET_NAME, mSchema, mUser, storage.createStore(mSchema), mGhostStore);
        
    }

    public void testBucketName(){
        assertEquals(BUCKET_NAME, mBucket.getName());
        assertEquals(mSchema.getRemoteName(), mBucket.getRemoteName());
    }
}
