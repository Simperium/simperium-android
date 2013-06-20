package com.simperium.testapp;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;

import android.util.Log;

import com.simperium.client.Simperium;
import com.simperium.util.Logger;
import com.simperium.storage.MemoryStore;
import com.simperium.client.User;
import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.Change;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.lang.Thread;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.simperium.testapp.MainActivityTest \
 * com.simperium.testapp.tests/android.test.InstrumentationTestRunner
 */
public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public static String TAG = MainActivity.TAG;
    private MainActivity mActivity;

    private Simperium mClient1;
    private Simperium mClient2;

    private User mUser1;
    private User mUser2;

    public MainActivityTest() {
        super("com.simperium.testapp", MainActivity.class);
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
        BucketSchema farmSchema = new Farm.Schema();
        Bucket<Farm> bucket1 = mClient1.bucket(farmSchema);
        bucket1.reset();
        bucket1.start();
        // Bucket<Farm> bucket2 = mClient1.bucket(farmSchema);
        // connectClients();
        Farm farm = bucket1.newObject();
        farm.getProperties().put("name", (String)"augusta");
        Change<Farm> change = farm.save();
        while(change.isPending()){
            try {
                Thread.sleep(100);
            } catch (java.lang.InterruptedException e) {
                fail("Interrupted with pending change");
            }
        }
        assertFalse(String.format("Farm is new %s", farm), farm.isNew());
        change = farm.delete();
        while(change.isPending()){
            try {
                Thread.sleep(100);
                Logger.log("Change is pending");
            } catch (java.lang.InterruptedException e) {
                fail("Interruped waiting for deletion");
            }
        }
        assertNull(bucket1.getObject(farm.getSimperiumKey()));
        // let's delete everything from the bucket
        // List<Farm> farms = bucket1.allEntities();
        // List<Change<Farm>> changes = new ArrayList<Change<Farm>>();
        // Iterator<Farm> farmIterator = farms.iterator();
        // while(farmIterator.hasNext()){
        //     farm = farmIterator.next();
        //     changes.add(farm.delete());
        // }
        // Iterator<Change<Farm>> changeIterator;
        // while(changes.size() > 0){
        //     changeIterator = changes.iterator();
        //     while(changeIterator.hasNext()){
        //         if(!changeIterator.next().isPending()){
        //             changeIterator.remove();
        //         }
        //     }
        // }
        // assertSame(0, bucket1.allEntities().size());
    }

    public boolean connectClients(){
        Log.d(TAG, "Connecting clients ...");
        while(!mClient1.isConnected() && !mClient2.isConnected()){
            if (!mClient1.isConnecting())
                mClient1.connect();
            if (!mClient2.isConnecting())
                mClient2.connect();
            try {
                Thread.sleep(200);
            } catch (java.lang.InterruptedException e) {
                return false;
            }
            Log.d(TAG, "Connecting ...");
        }
        Log.d(TAG, "Connected");
        return true;
    }

}
