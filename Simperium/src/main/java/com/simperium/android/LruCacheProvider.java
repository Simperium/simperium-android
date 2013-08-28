package com.simperium.android;

import com.simperium.client.Syncable;
import com.simperium.client.ObjectCacheProvider;
import com.simperium.client.ObjectCacheProvider.ObjectCache;

import com.simperium.util.Logger;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import android.util.LruCache;

public class LruCacheProvider implements ObjectCacheProvider {

    public <T extends Syncable> LruObjectCache<T> buildCache(){
        return new LruObjectCache<T>();
    }

    private class LruObjectCache<T extends Syncable> implements ObjectCache<T> {

        private static final int MAX_ENTRIES=32;

        LruCache<String, T> mCache = new LruCache<String, T>(MAX_ENTRIES);

        public T get(String key){
            return mCache.get(key);
        }

        public void put(String key, T object){
            mCache.put(key, object);
        }

        public void remove(String key){
            mCache.remove(key);
        }

    }

}

