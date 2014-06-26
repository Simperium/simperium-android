/**
 * A nice fake client
 */
package com.simperium.test;

import com.simperium.client.ClientFactory;
import com.simperium.storage.MemoryStore;

public class MockClient implements ClientFactory {

    public MockAuthProvider authProvider = new MockAuthProvider();
    public MockChannelProvider channelProvider = new MockChannelProvider();

    @Override
    public MockAuthProvider buildAuthProvider(String appId, String appSecret){
        return authProvider;
    }

    @Override
    public MockChannelProvider buildChannelProvider(String appId){
        return channelProvider;
    }

    @Override
    public MemoryStore buildStorageProvider(){
        return new MemoryStore();
    }

    @Override
    public MockGhostStore buildGhostStorageProvider(){
        return new MockGhostStore();
    }

    @Override
    public MockExecutor.Immediate buildExecutor(){
        return MockExecutor.immediate();
    }

}