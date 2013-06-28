package com.simperium.tests;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;

import android.util.Log;

import com.simperium.Simperium;
import com.simperium.util.Logger;
import com.simperium.storage.MemoryStore;
import com.simperium.client.User;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.BucketSchema;
import com.simperium.client.Change;

import com.simperium.tests.models.Farm;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.simperium.tests.MainActivityTest \
 * com.simperium.tests.tests/android.test.InstrumentationTestRunner
 */
public class BasicSyncTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public static String TAG = MainActivity.TAG;
    private MainActivity mActivity;

    private Simperium mClient1;
    private Simperium mClient2;

    private User mUser1;
    private User mUser2;

    public BasicSyncTest() {
        super("com.simperium.tests", MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {

        super.setUp();
        Logger.log(String.format("Test thread %s", Thread.currentThread().getName()));
        setActivityInitialTouchMode(false);
        mActivity = getActivity();

        // load up simperium id's, keys and tokens
        String appId = mActivity.getSimperiumAppId();
        String appSecret = mActivity.getSimperiumAppSecret();
        String userToken = mActivity.getSimperiumUserToken();

        // configure two seperate clients
        mClient1 = new Simperium(appId, appSecret, mActivity, new MemoryStore());
        mClient2 = new Simperium(appId, appSecret, mActivity, new MemoryStore());

        // get users for each client
        mUser1 = mClient1.getUser();
        mUser2 = mClient2.getUser();

        // Set the user's access token
        mUser1.setAccessToken(userToken);
        mUser2.setAccessToken(userToken);

    }
    
    @Override
    protected void tearDown() throws Exception {
        mClient1.disconnect();
        mClient2.disconnect();
        super.tearDown();
    }

    public void testSimperiumConfiguration(){
        // id and secret should be configured
        assertNotEqual("", mActivity.getSimperiumAppId());
        assertNotEqual("", mActivity.getSimperiumAppSecret());
        // users should have been provided with access tokens
        assertFalse(String.format("client1 needs an access token %s", mClient1.getUser()),
            mClient1.getUser().needsAuthentication());

        assertFalse(String.format("client2 needs an access token %s", mClient2.getUser()),
            mClient2.getUser().needsAuthentication());
    }

    /**
     * Test that we can create an object and then delete it
     * uses the live Simperium api
     */
    public void testObjectCreationAndDeletion(){
        BucketSchema farmSchema = new Farm.Schema("basic");
        Bucket<Farm> bucket1 = mClient1.bucket(farmSchema);
        bucket1.reset();
        bucket1.start();
        // Bucket<Farm> bucket2 = mClient1.bucket(farmSchema);
        // connectClients();
        Farm farm = bucket1.newObject();
        farm.getProperties().put("name", (String)"augusta");
        waitFor(farm.save());
        assertFalse(String.format("Farm is new %s", farm), farm.isNew());
        waitFor(farm.delete());

        try {
            farm = bucket1.getObject(farm.getSimperiumKey());
        } catch (BucketObjectMissingException e) {
            farm = null;
        } finally {
            assertNull(farm);
        }
    }

    public boolean connectClients(){
        Log.d(TAG, "Connecting clients ...");
        while(!mClient1.isConnected() && !mClient2.isConnected()){
            if (!mClient1.isConnecting())
                mClient1.connect();
            if (!mClient2.isConnecting())
                mClient2.connect();
            waitFor(1);
            Log.d(TAG, "Connecting ...");
        }
        waitFor(2);
        Log.d(TAG, "Connected");
        return true;
    }

    protected void waitFor(int seconds){
        try {
            Thread.sleep(seconds * (long)1000);
        } catch (InterruptedException e) {
            Logger.log("Interupted");
        }
    }

    protected void waitFor(Change change){
        long timeout = 5000; // 5 second timeout
        long start = System.currentTimeMillis();
        Logger.log(TAG, String.format("Waiting for change %s", change));
        while(change.isPending()){
            waitFor(1);
            if (System.currentTimeMillis() - start > timeout) {
                throw( new RuntimeException("Change timed out") );
            }
        }
        Logger.log(TAG, String.format("Done waiting %s", change));
    }

}
