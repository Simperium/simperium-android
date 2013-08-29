package com.simperium.testapp.mock;

import com.simperium.client.AuthProvider;
import com.simperium.client.User;

public class MockAuthProvider implements AuthProvider {

    @Override
    public void setAuthProvider(String name){}

    @Override
    public void createUser(User user, User.AuthResponseHandler handler){
        user.setAccessToken("user-create-token");
        handler.onSuccess(user);
    }

    @Override
    public void authorizeUser(User user, User.AuthResponseHandler handler){
        // just call success callback
        user.setAccessToken("user-auth-token");
        handler.onSuccess(user);
    }

    @Override
    public String getAccessToken(){
        return null;
    }

    @Override
    public void setAccessToken(String token){
    }

    @Override
    public void clearAccessToken(){
    }

}