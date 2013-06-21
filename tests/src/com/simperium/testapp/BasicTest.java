package com.simperium.testapp;

import com.simperium.testapp.models.Farm;

import com.simperium.Simperium;
import com.simperium.client.Bucket;
import com.simperium.client.Change;
import com.simperium.client.Syncable;
import com.simperium.util.Uuid;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class BasicTest extends SimperiumTest {

    private List<Simperium> clients;
    private List<Bucket<Farm>> buckets;
    private Bucket<Farm> leader;

    protected void setUp() throws Exception {
        super.setUp();
        clients = createClients(3);
        String bucketSuffix = Uuid.uuid().substring(0,6);
        buckets = createAndStartBuckets(clients, new Farm.Schema(bucketSuffix));
        leader = buckets.get(0);
    }

    protected void tearDown() throws Exception {
        disconnectClients(clients);
        super.tearDown();
    }

    public void testAddingSingleObject(){
        Farm config = leader.newObject("config");
        config.getProperties().put("warpSpeed", 2);
        assertTrue(config.isNew());
        Change<Farm> change = config.save();
        while(change.isPending()){
            waitFor(1);
        };
        assertFalse(config.isNew());
        // wait until the other's have a config
        List<Bucket<Farm>> pending = buckets.subList(1, buckets.size());
        while(pending.size() > 0){
            Iterator<Bucket<Farm>> iterator = pending.iterator();
            while(iterator.hasNext()){
                if (iterator.next().containsKey("config")) {
                    iterator.remove();
                }
            }
            waitFor(1);
        }
        assertBucketsEqualForKey(buckets, "config");
    }

    public void testDeletingSingleObject(){
        Farm config = leader.newObject("config");
        config.getProperties().put("warpSpeed", 2);
        Change add = config.save();
        while(add.isPending()){
            waitFor(1);
        }
        Change delete = config.delete();
        while(delete.isPending()){
            waitFor(1);
        }
        // wait until all buckets have the correct change version
        String version = leader.getChangeVersion();
        List<Bucket<Farm>> checking = new ArrayList<Bucket<Farm>>(buckets);
        while(checking.size() > 0){
            Iterator<Bucket<Farm>> iterator = checking.iterator();
            while(iterator.hasNext()){
                if (iterator.next().getChangeVersion().equals(version)) {
                    iterator.remove();
                }
            }
            waitFor(1);
        }
        Iterator<Bucket<Farm>> bucketIterator = buckets.iterator();
        while(bucketIterator.hasNext()){
            Bucket<Farm> bucket = bucketIterator.next();
            assertFalse(String.format("Bucket %s still has config", bucket), bucket.containsKey("config"));
        }
    }

    protected <T extends Syncable> void assertBucketsEqualForKey(List<Bucket<T>> buckets, String key){
        assertTrue(String.format("Buckets were not equal for key %s", key), bucketsEqualForKey(buckets, key));
    }

}