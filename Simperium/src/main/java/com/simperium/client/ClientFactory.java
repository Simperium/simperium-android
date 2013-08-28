/**
 * Work in progress. Decoupling the pieces of a Simperium client to
 * allow alternate implementations for different platforms and dependency
 * injection for testing.
 */
package com.simperium.client;

import com.simperium.storage.StorageProvider;

public interface ClientFactory {

    public interface AuthProvider {
        // TODO: refactor this out
        void setAuthProvider(String name);

        void createUser(User user, User.AuthResponseHandler handler);
        void authorizeUser(User user, User.AuthResponseHandler handler);

        String getAccessToken();
        void setAccessToken(String token);
        void clearAccessToken();
    }

    public interface ChannelProvider {

        Bucket.ChannelProvider createChannel(Bucket bucket);

    }


    public AuthProvider buildAuthProvider(String appId, String appSecret);
    public ChannelProvider buildChannelProvider(String appId);
    public StorageProvider buildStorageProvider();
    public GhostStorageProvider buildGhostStorageProvider();

}
