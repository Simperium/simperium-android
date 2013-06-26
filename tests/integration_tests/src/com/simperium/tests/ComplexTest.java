package com.simperium.tests;

import com.simperium.tests.models.Farm;

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

public class ComplexTest extends SimperiumTest {

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

    public void testChangesToMultipleObjects(){
        // create an object that gets synced
        Farm config = leader.newObject();
        config.put("warpSpeed", 2);
        config.put("captainsLog", "Hi");
        config.put("shieldPercent", 3.14);
        // save and wait for ack
        waitFor(config.save());
        // Now change the same object in each bucket
    }

}
