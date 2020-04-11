package com.simperium.client;

/**
 * For use with Simperium.createUser and Simperium.authorizeUser
 */
public interface AuthResponseListener {
    public void onSuccess(User user, String userId, String token);
    public void onFailure(User user, AuthException error);
}
