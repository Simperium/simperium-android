package com.simperium.client;

public interface ObjectCacheProvider {

    public <T extends Syncable> ObjectCache<T> buildCache();

    public interface ObjectCache<T extends Syncable> {
        public T get(String key);
        public void put(String key, T object);
        public void remove(String key);
    }

}

