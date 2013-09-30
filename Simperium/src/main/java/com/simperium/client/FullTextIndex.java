package com.simperium.client;

import android.content.ContentValues;

import java.util.Map;

public class FullTextIndex<T extends Syncable> {

    public interface Indexer<T extends Syncable> {
        public ContentValues index(String[] keys, T object);
    }

    private final Indexer mIndexer;
    private final String[] mKeys;

    FullTextIndex(String ... keys){
        this(new Indexer<T>() {

            @Override
            public ContentValues index(String[] keys, T object){
                ContentValues indexValues = new ContentValues(keys.length);
                Map<String,Object> values = object.getDiffableValue();
                for (String key : keys) {
                    Object value = values.get(key);
                    if (value != null) {
                        indexValues.put(key, value.toString());
                    }
                }
                return indexValues;
            }

        }, keys);
    }

    FullTextIndex(Indexer<T> indexer, String ... keys){
        mIndexer = indexer;
        mKeys = keys;
    }

    public String[] getKeys(){
        return mKeys;
    }

    public ContentValues index(T object){
        return mIndexer.index(mKeys, object);
    }

}