package com.simperium.testapp;

import com.simperium.testapp.models.Farm;

import com.simperium.Simperium;
import com.simperium.client.Bucket;
import com.simperium.client.Change;
import com.simperium.client.Syncable;
import com.simperium.util.Uuid;
import com.simperium.util.Logger;

import java.util.List;
import java.util.Map;
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
        waitFor(config.save());
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
        waitFor(config.save());
        waitFor(config.delete());
        
        // wait until all buckets have the correct change version
        waitForBucketsToMatchChangeVersion(buckets, leader.getChangeVersion());
        Iterator<Bucket<Farm>> bucketIterator = buckets.iterator();
        while(bucketIterator.hasNext()){
            Bucket<Farm> bucket = bucketIterator.next();
            assertFalse(String.format("Bucket %s still has config", bucket), bucket.containsKey("config"));
        }
    }

    public void testChangesToSingleObject(){
        Farm config = leader.newObject("config");
        config.getProperties().put("warpSpeed", 1);
        config.getProperties().put("captainsLog", "Hi");
        config.getProperties().put("shieldPercent", 3.14);
        config.getProperties().put("cost", 3.00);
        waitFor(config.save());

        Integer warpSpeed = new Integer(4);
        String captainsLog = (String) "Hi!!!";
        Float shieldPercent = new Float(2.718);
        Float cost = new Float(4.00);
        config.getProperties().put("warpSpeed", warpSpeed);
        config.getProperties().put("captainsLog", captainsLog);
        config.getProperties().put("shieldPercent", shieldPercent);
        config.getProperties().put("cost", cost);
        waitFor(config.save());

        Map<String,Object> properties = config.getProperties();
        assertEquals(warpSpeed, properties.get("warpSpeed"));
        assertEquals(captainsLog, properties.get("captainsLog"));
        assertEquals(shieldPercent, properties.get("shieldPercent"));
        assertEquals(cost, properties.get("cost"));
        
        waitForBucketsToMatchChangeVersion(buckets, leader.getChangeVersion());
        assertBucketsEqualForKey(buckets, "config");
    }

    public void testPendingChange(){
        Farm config = leader.newObject("config");
        config.getProperties().put("warpSpeed", 2);
        Change save = config.save();
        while(!save.isSent()){
            waitFor((float) 0.01);
        }
        config.getProperties().put("warpSpeed", 3);
        waitFor(config.save());

        assertEquals(3, config.getUnmodifiedValue().get("warpSpeed"));
        waitForBucketsToMatchChangeVersion(buckets, leader.getChangeVersion());
        assertBucketsEqualForKey(buckets, "config");
    }
    protected <T extends Syncable> void assertBucketsEqualForKey(List<Bucket<T>> buckets, String key){
        assertTrue(String.format("Buckets were not equal for key %s", key), bucketsEqualForKey(buckets, key));
    }

    protected void waitFor(Change change){
        Logger.log(TAG, String.format("Waiting for change %s", change));
        while(change.isPending()){
            waitFor(1);
        }
        Logger.log(TAG, String.format("Done waiting %s", change));
    }

    protected <T extends Syncable> void waitForBucketsToMatchChangeVersion(List<Bucket<T>> buckets, String version){
        Logger.log(TAG, String.format("Waiting for buckets to reach cv %s", version));
        List<Bucket<T>> checking = new ArrayList<Bucket<T>>(buckets);
        while(checking.size() > 0){
            Logger.log(TAG, String.format("Waiting on %d buckets", checking.size()));
            Iterator<Bucket<T>> iterator = checking.iterator();
            while(iterator.hasNext()){
                if (iterator.next().getChangeVersion().equals(version)) {
                    iterator.remove();
                }
            }
            waitFor(1);
        }
        Logger.log(TAG, "Buckets match");
    }
}