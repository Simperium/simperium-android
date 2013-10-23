package com.simperium.test;

import com.simperium.client.ObjectCacheProvider.ObjectCache;
import com.simperium.client.Syncable;

public class MockCache<T extends Syncable> implements ObjectCache<T> {

    @Override
    public T get(String key){
        return null;
    }

    @Override
    public void put(String key, T object){
        // no-op
    }

    @Override
    public void remove(String key){
        // no-op
    }

}