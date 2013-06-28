package com.simperium.client;

import java.util.Map;
/**
 * An interface to allow applications to provide a schema for a bucket and a way
 * instatiate custom BucketObject instances
 */
public abstract class BucketSchema<T extends Syncable> {

    // public interface Indexer<T extends Syncable> {
    //     
    // }
    // 
    // public static class StringIndexer implements Indexer<Syncable> {
    //     @Override
    //         public void index()
    // }

    // private List<Indexer<T>> mIndexers = new ArrayList<Indexer<T>>();

    public abstract String getRemoteName();
    public abstract T build(String key, Map<String,Object>properties);

    public void addIndex(String key){
        // addIndex(new StringIndexer(key));
    }
    // 
    // public void addIndex(Indexer<T> indexer){
    //     mIndexers.add(indexer);
    // }
}
