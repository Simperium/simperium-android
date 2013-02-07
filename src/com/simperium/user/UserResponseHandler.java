package com.simperium.user;

import com.simperium.user.User;
import org.json.JSONObject;

public class UserResponseHandler {
    
    public void onSuccess(User user){};
    public void onInvalid(User user, Throwable error, JSONObject validationErrors){};
    public void onFailure(User user, Throwable error, String message){};
}