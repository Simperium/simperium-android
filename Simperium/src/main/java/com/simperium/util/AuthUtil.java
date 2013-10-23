package com.simperium.util;

import com.simperium.client.User;

import org.json.JSONException;
import org.json.JSONObject;

public class AuthUtil {

    public static final String USERNAME_KEY = "username";
    public static final String ACCESS_TOKEN_KEY = "access_token";
    public static final String PASSWORD_KEY = "password";
    public static final String USERID_KEY = "userid";
    public static final String PROVIDER_KEY = "provider";

    public static JSONObject makeAuthRequestBody(User user){
        return makeAuthRequestBody(user.getEmail(), user.getPassword());
    }

    public static JSONObject makeAuthRequestBody(CharSequence username, CharSequence password){
        JSONObject body = new JSONObject();
        try {
            if (username != null) body.put(USERNAME_KEY, username);
        } catch (JSONException e) {
            // could not set username
        }
        try {
            if (password != null) body.put(PASSWORD_KEY, password);
        } catch (JSONException e) {
            // could not set password
        }
        return body;
    }

}
