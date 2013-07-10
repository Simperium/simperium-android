package com.simperium.tests.mock;

import com.simperium.client.ObjectCache.Provider;
import com.simperium.client.Syncable;

public class MockCache<T extends Syncable> implements Provider<T> {

    @Override
    public T get(String key){
        return null;
    }

    public void put(String key, T object){
        // no-op
    }

    public void remove(String key){
        // no-op
    }

}