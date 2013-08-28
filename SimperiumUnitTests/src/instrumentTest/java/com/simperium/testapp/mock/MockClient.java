/**
 * A nice fake client
 */
package com.simperium.testapp.mock;

import com.simperium.client.ClientFactory;
import com.simperium.client.User;
import com.simperium.client.Bucket;

import com.simperium.storage.MemoryStore;

public class MockClient implements ClientFactory {

    public String accessToken = "fake-token";

    @Override
    public MockAuthProvider buildAuthProvider(String appId, String appSecret){
        return new MockAuthProvider();
    }

    @Override
    public MockChannelProvider buildChannelProvider(String appId){
        return new MockChannelProvider();
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
    public MockCacheProvider buildObjectCacheProvider(){
        return new MockCacheProvider();
    }

    @Override
    public MockSyncService buildSyncService(){
        return new MockSyncService();
    }

    private class MockAuthProvider implements ClientFactory.AuthProvider {

        @Override
        public void setAuthProvider(String name){}

        @Override
        public void createUser(User user, User.AuthResponseHandler handler){
            user.setAccessToken("fake-token");
            handler.onSuccess(user);
        }

        @Override
        public void authorizeUser(User user, User.AuthResponseHandler handler){
            // just call success callback
            user.setAccessToken("fake-token");
            handler.onSuccess(user);
        }

        @Override
        public String getAccessToken(){
            return accessToken;
        }

        @Override
        public void setAccessToken(String token){
            accessToken = token;
        }

        @Override
        public void clearAccessToken(){
            accessToken = null;
        }

    }

    private class MockChannelProvider implements ClientFactory.ChannelProvider {

        public MockChannel createChannel(Bucket bucket){
            return new MockChannel(bucket);
        }

    }

}