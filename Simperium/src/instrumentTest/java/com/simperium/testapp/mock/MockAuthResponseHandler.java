package com.simperium.testapp.mock;

import com.simperium.client.User;

import org.json.JSONObject;

public class MockAuthResponseHandler implements User.AuthResponseHandler {

    public boolean success = false, invalid = false, failed = false;

    // user used in callback
    public User user;

    // if onInvalid or onFailure
    public Throwable error;
    
    // if onInvalid is called
    public JSONObject validationErrors;

    // if onFailure is called
    public String message;

    public void onSuccess(User user){
        success = true;
        this.user = user;
    }

    public void onInvalid(User user, Throwable error, JSONObject validationErrors){
        invalid = true;
        this.user = user;
        this.error = error;
        this.validationErrors = validationErrors;
    }

    public void onFailure(User user, Throwable error, String message){
        failed = true;
        this.user = user;
        this.error = error;
        this.message = message;
    }

}