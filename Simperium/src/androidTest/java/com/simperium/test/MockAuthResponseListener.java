package com.simperium.test;

import com.simperium.client.AuthException;
import com.simperium.client.AuthResponseListener;
import com.simperium.client.User;

public class MockAuthResponseListener implements AuthResponseListener {

    public boolean success = false, failure = false;

    public User user;
    public AuthException exception;

    @Override
    public void onSuccess(User user){
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