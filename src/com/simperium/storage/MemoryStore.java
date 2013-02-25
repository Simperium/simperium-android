package com.simperium.client.storage;

import com.simperium.client.Simperium;
import com.simperium.client.StorageProvider;
import com.simperium.client.Bucket;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import android.util.Pair;
/**
 * Very naive storage system for testing. Not thread safe. Maps need to be threadsafe
 */
public class MemoryStore implements StorageProvider {
    private Map<Pair<String,String>, Bucket.Diffable> entities = new HashMap<Pair<String,String>, Bucket.Diffable>();
    private Map<Pair<String,String>, Integer> versions = new HashMap<Pair<String,String>, Integer>();
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
    public List<Bucket.Diffable> allEntities(Bucket bucket){
        ArrayList<Bucket.Diffable> bucketEntities = new ArrayList<Bucket.Diffable>();
        Iterator<Pair<String,String>> keySet = entities.keySet().iterator();
        while(keySet.hasNext()){
            Pair<String,String> keyPair = keySet.next();
            if (keyPair.first.equals(bucket.getName())) {
                bucketEntities.add(entities.get(keyPair));
            }
        }
        return bucketEntities;
    }
    public void addObject(Bucket bucket, String key, Bucket.Diffable object){
        Simperium.log(String.format("Saving object %s in thread %s %s", key, Thread.currentThread().getName(), object.getDiffableValue()));
        Pair bucketKey = bucketKey(bucket, key);
        entities.put(bucketKey, object);
        versions.put(bucketKey, object.getVersion());
    }
    public void updateObject(Bucket bucket, String key, Bucket.Diffable object){
        addObject(bucket, key, object);
    }
    public void removeObject(Bucket bucket, String key){
    }
    public Bucket.Diffable getObject(Bucket bucket, String key){
        Simperium.log(String.format("Requesting object %s in thread %s", key, Thread.currentThread().getName()));
        Bucket.Diffable object = entities.get(bucketKey(bucket, key));
        Simperium.log(String.format("Found object: %s %s", object, object.getDiffableValue()));
        return object;
    }
    public Boolean containsKey(Bucket bucket, String key){
        return entities.containsKey(bucketKey(bucket, key));
    }
    public Boolean hasKeyVersion(Bucket bucket, String key, Integer version){
        Integer localVersion = getKeyVersion(bucket, key);
        return localVersion != null && localVersion >= version;
    }
    public Integer getKeyVersion(Bucket bucket, String key){
        return versions.get(bucketKey(bucket, key));
    }
    public String getChangeVersion(Bucket bucket){
        return bucketVersions.get(bucket.getName());
    }
    public Boolean hasChangeVersion(Bucket bucket){
        return bucketVersions.containsKey(bucket.getName());
    }
    public Boolean hasChangeVersion(Bucket bucket, String version){
        String localVersion = bucketVersions.get(bucket.getName());
        return version == localVersion;
    }
    public void setChangeVersion(Bucket bucket, String string){
        bucketVersions.put(bucket.getName(), string);
    }
    
}