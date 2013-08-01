package com.simperium.client;

import java.util.Map;

public interface ChannelProvider<T extends Syncable> {
    public Change<T> queueLocalChange(T object);
    public Change<T> queueLocalDeletion(T object);
    public RevisionsRequest getRevisions(String key, int sinceVersion, RevisionsRequestCallbacks callbacks);
    public boolean isIdle();
    public void start();
    public void reset();

    public interface RevisionsRequest {
        public boolean isComplete();
    }
    
    public interface RevisionsRequestCallbacks {
        public void onError(Throwable exception);
        public void onRevision(String key, int version, Map<String,Object> properties);
        public void onComplete();
    }
}
