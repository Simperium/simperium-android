package com.simperium.test;

import com.simperium.client.AuthException;
import com.simperium.client.AuthProvider;
import com.simperium.client.AuthResponseListener;
import com.simperium.client.User;

public class MockAuthResponseListener implements AuthResponseListener {
    public AuthException exception;
    public User user;
    public boolean success = false, failure = false;

    @Override
    public void onSuccess(User user, String userId, String token, AuthProvider provider){
        success = true;
        this.user = user;
    }

    @Override
    public void onFailure(User user, AuthException exception){
        failure = true;
        this.user = user;
        this.exception = exception;
    }
}
