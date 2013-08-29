package com.simperium.client;

public interface AuthProvider {
    void setAuthProvider(String name);

    void createUser(User user, User.AuthResponseHandler handler);
    void authorizeUser(User user, User.AuthResponseHandler handler);

    String getAccessToken();
    void setAccessToken(String token);
    void clearAccessToken();
    
}