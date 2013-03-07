package com.simperium.client.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.simperium.client.Bucket;
import com.simperium.client.Simperium;
import com.simperium.client.StorageProvider;

import android.util.Pair;

/**
 * Very naive storage system for testing. Not thread safe. Maps need to be threadsafe
 */
public class MemoryStore implements StorageProvider {
    private Map<Pair<String,String>, Bucket.Syncable> entities = new HashMap<Pair<String,String>, Bucket.Syncable>();
    private Map<String, String> bucketVersions = new HashMap<String,String>();

    // Prepare the datastore to store entites for a bucket
    public void initialize(Bucket bucket){

    }

    private Pair bucketKey(Bucket bucket, String key){
        return Pair.create(bucket.getName(), key);
    }
    /**
     * not optimal, go through each key and add any object where pair[0] matches bucket
     */
    public List<Bucket.Syncable> allEntities(Bucket bucket){
        ArrayList<Bucket.Syncable> bucketEntities = new ArrayList<Bucket.Syncable>();
        Iterator<Pair<String,String>> keySet = entities.keySet().iterator();
        while(keySet.hasNext()){
            Pair<String,String> keyPair = keySet.next();
            if (keyPair.first.equals(bucket.getName())) {
                bucketEntities.add(entities.get(keyPair));
            }
        }
        return bucketEntities;
    }
    public void addObject(Bucket bucket, String key, Bucket.Syncable object){
        Pair bucketKey = bucketKey(bucket, key);
        entities.put(bucketKey, object);
    }
    public void updateObject(Bucket bucket, String key, Bucket.Syncable object){
        addObject(bucket, key, object);
    }
    public void removeObject(Bucket bucket, String key){
        Pair bucketKey = bucketKey(bucket, key);
        entities.remove(bucketKey);
    }
    public Bucket.Syncable getObject(Bucket bucket, String key){
        Bucket.Syncable object = entities.get(bucketKey(bucket, key));
        return object;
    }

}
