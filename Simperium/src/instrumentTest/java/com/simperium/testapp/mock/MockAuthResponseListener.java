package com.simperium.testapp.mock;

import com.simperium.client.User;
import com.simperium.client.AuthResponseListener;

import org.json.JSONObject;

public class MockAuthResponseListener implements AuthResponseListener {

    public boolean success = false, error = false, failure = false;

    public User user;
    public Throwable throwable;
    public String message;

    @Override
    public void onSuccess(User user){
        success = true;
        this.user = user;
    }

    @Override
    public void onFailure(User user, String message){
        failure = true;
        this.user = user;
        this.message = message;
    }

    @Override
    public void onError(User user, Throwable throwable){
        error = true;
        this.user = user;
        this.throwable = throwable;
    }

}