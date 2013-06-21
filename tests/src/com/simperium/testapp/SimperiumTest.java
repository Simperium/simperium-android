package com.simperium.testapp;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;

import com.simperium.Simperium;
import com.simperium.util.Logger;
import com.simperium.util.Uuid;
import com.simperium.storage.MemoryStore;
import com.simperium.client.User;
import com.simperium.client.Bucket;
import com.simperium.client.Syncable;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.BucketSchema;
import com.simperium.client.Change;

import com.simperium.testapp.models.Farm;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Duplicating tests from iOS project
 */
public class SimperiumTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public static String TAG = MainActivity.TAG;
    protected MainActivity mActivity;

    protected Simperium mClient;

    protected User mUser;
    protected String mAppId, mAppSecret, mUserToken;

    public SimperiumTest() {
        super("com.simperium.testapp", MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {

        super.setUp();

        setActivityInitialTouchMode(false);
        mActivity = getActivity();

        // load up simperium id's, keys and tokens
        mAppId = mActivity.getSimperiumAppId();
        mAppSecret = mActivity.getSimperiumAppSecret();
        mUserToken = mActivity.getSimperiumUserToken();

    }

    protected void waitFor(int seconds){
        try {
            Thread.sleep(seconds * (long)1000);
        } catch (InterruptedException e) {
            Logger.log("Interupted");
        }
    }

    protected void waitFor(float seconds){
        try {
            Thread.sleep((long)(seconds * (long)1000));
        } catch (InterruptedException e) {
            Logger.log("Interupted");
        }
    }

    protected boolean bucketsDone(List<Bucket> buckets){
        Iterator<Bucket> bucketIterator = buckets.iterator();
        while(bucketIterator.hasNext()){
            if (!bucketIterator.next().isIdle()) {
                return false;
            }
        }
        return true;
    }

    protected void waitForCompletion(int timeout, List<Bucket> buckets){
        while(true){
            if (bucketsDone(buckets)) {
                break;
            }
            waitFor(timeout);
        }
    }

    protected String uniqueBucketNameFor(String bucketName){
        return String.format("%s-%s", bucketName, Uuid.uuid());
    }

    protected Simperium createClient(){
        Simperium client = new Simperium(mAppId, mAppSecret, getActivity(), new MemoryStore());
        client.getUser().setAccessToken(mUserToken);
        return client;
    }

    protected List<Simperium> createClients(int count){
        List<Simperium> clients = new ArrayList<Simperium>(count);
        for (int i=0; i<count; i++) {
            clients.add(createClient());
        }
        return clients;
    }

    protected <T extends Syncable> Bucket<T> createBucket(Simperium client, String name, BucketSchema<T> schema){
        return client.bucket(name, schema);
    }

    protected <T extends Syncable> List<Bucket<T>> createBuckets(List<Simperium> clients, BucketSchema<T> schema){
        int count = clients.size();
        List<Bucket<T>> buckets = new ArrayList<Bucket<T>>(count);
        for (int i=0; i<count; i++) {
            buckets.add(createBucket(clients.get(i), String.format("client%d%s", i, schema.getRemoteName()), schema));
        }
        return buckets;
    }

    protected <T extends Syncable> List<Bucket<T>> createAndStartBuckets(List<Simperium> clients, BucketSchema<T> schema){
        return startBuckets(createBuckets(clients, schema));
    }

    protected <T extends Syncable> List<Bucket<T>> startBuckets(List<Bucket<T>> buckets){
        Iterator<Bucket<T>> iterator = buckets.iterator();
        while(iterator.hasNext()){
            iterator.next().start();
        }
        return buckets;
    }

    protected List<Simperium> disconnectClients(List<Simperium> clients){
        Iterator<Simperium> iterator = clients.iterator();
        while(iterator.hasNext()){
            iterator.next().disconnect();
        }
        return clients;
    }

    protected <T extends Syncable> boolean bucketsEqualForKey(List<Bucket<T>> buckets, String key){
        // First bucket is the one to compare to
        List<Bucket> copied = new ArrayList<Bucket>(buckets);
        Bucket leader = copied.remove(0);
        Iterator<Bucket> iterator = copied.iterator();
        try {
            Syncable object = leader.getObject(key);          
            while(iterator.hasNext()){
                Syncable other = iterator.next().getObject(key);
                if(!object.getVersion().equals(other.getVersion()) || !object.getUnmodifiedValue().equals(other.getUnmodifiedValue())){
                    return false;
                }
            }
              
        } catch (BucketObjectMissingException e) {
            return false;
        }

        return true;
    }
    
    protected boolean ensureBucketEqualToBucket(Bucket bucket1, Bucket bucket2){
        return false;
    }

    protected <T extends Syncable> boolean ensureBucketsHaveSameChangeVersion(List<Bucket<T>> buckets){
        String version = buckets.get(0).getChangeVersion();
        Iterator<Bucket<T>> others = buckets.subList(1, buckets.size()).iterator();
        while(others.hasNext()){
            if(!version.equals(others.next().getChangeVersion())){
                return false;
            }
        }
        return true;
    }

}
