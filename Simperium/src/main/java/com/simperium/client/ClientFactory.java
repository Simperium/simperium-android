/**
 * Work in progress. Decoupling the pieces of a Simperium client to
 * allow alternate implementations for different platforms and dependency
 * injection for testing.
 */
package com.simperium.client;

import com.simperium.storage.StorageProvider;
import com.simperium.client.ObjectCacheProvider;

public interface ClientFactory {

    public AuthProvider buildAuthProvider(String appId, String appSecret);
    public ChannelProvider buildChannelProvider(String appId);
    public StorageProvider buildStorageProvider();
    public GhostStorageProvider buildGhostStorageProvider();
    public ObjectCacheProvider buildObjectCacheProvider();
    public SyncService buildSyncService();

}
