package com.simperium.test;

import com.simperium.client.ObjectCacheProvider;
import com.simperium.client.Syncable;

public class MockCacheProvider implements ObjectCacheProvider {

    @Override
    public <T extends Syncable> MockCache<T> buildCache(){
        return new MockCache<T>();
    };

}

