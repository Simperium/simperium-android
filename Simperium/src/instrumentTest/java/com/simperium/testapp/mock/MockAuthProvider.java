package com.simperium.testapp.mock;

import com.simperium.client.AuthProvider;
import com.simperium.client.User;

public class MockAuthProvider implements AuthProvider {

    public String accessToken = "fake-token";
    public String email = "user@example.com";

    @Override
    public void setAuthProvider(String name){}

    @Override
    public void createUser(User user, User.AuthResponseHandler handler){
        accessToken = "user-create-token";
        user.setAccessToken(accessToken);
        user.setEmail(email);
        handler.onSuccess(user);
    }

    @Override
    public void authorizeUser(User user, User.AuthResponseHandler handler){
        accessToken = "user-auth-token";
        user.setAccessToken(accessToken);
        user.setEmail(email);
        handler.onSuccess(user);
    }

    @Override
    public void restoreUser(User user){
        user.setAccessToken(accessToken);
        user.setEmail(email);
    }

    @Override
    public void deauthorizeUser(User user){
        email = null;
        accessToken = null;
    }

}