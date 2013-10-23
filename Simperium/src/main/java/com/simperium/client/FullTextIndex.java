package com.simperium.client;

import java.util.Map;
import java.util.Arrays;

public class FullTextIndex<T extends Syncable> {

    public interface Indexer<T extends Syncable> {
        public Map<String, String> index(String[] keys, T object);
    }

    private final Indexer mIndexer;
    private final String[] mKeys;

    FullTextIndex(Indexer<T> indexer, String ... keys){
        mIndexer = indexer;
        mKeys = keys;
    }

    public String[] getKeys(){
        return mKeys;
    }

    public Map<String,String> index(T object){
        return mIndexer.index(mKeys, object);
    }

    public int getColumnIndex(String columnName){
        if (columnName == null) return -1;
        return Arrays.asList(mKeys).indexOf(columnName);
    }

}