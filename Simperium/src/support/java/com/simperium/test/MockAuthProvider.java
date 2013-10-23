package com.simperium.test;

import com.simperium.client.AuthProvider;
import com.simperium.client.AuthResponseHandler;
import com.simperium.client.User;
import com.simperium.util.AuthUtil;

import org.json.JSONException;
import org.json.JSONObject;

public class MockAuthProvider implements AuthProvider {

    public String accessToken = "fake-token";
    public String email = "user@example.com";
    public String userId = "fake-id";

    @Override
    public void setAuthProvider(String name){}

    @Override
    public void createUser(JSONObject userDetails, AuthResponseHandler handler){
        accessToken = "user-create-token";
        handler.onResponse(makeResponse(email, userId, accessToken));
    }

    @Override
    public void authorizeUser(JSONObject userDetails, AuthResponseHandler handler){
        accessToken = "user-auth-token";
        handler.onResponse(makeResponse(email, userId, accessToken));
    }

    @Override
    public void saveUser(User user){
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

    public JSONObject makeResponse(String username, String userId, String accessToken){
        JSONObject object = new JSONObject();
        try {
            object.put(AuthUtil.ACCESS_TOKEN_KEY, accessToken);
        } catch (JSONException e) {
            // could not set username
        }
        try {
            object.put(AuthUtil.USERNAME_KEY, username);
        } catch (JSONException e) {
            // could not set username
        }
        try {
            object.put(AuthUtil.USERID_KEY, userId);
        } catch (JSONException e) {
            // could not set username
        }
        return object;
    }

}