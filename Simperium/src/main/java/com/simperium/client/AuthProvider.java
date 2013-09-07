package com.simperium.client;

import org.json.JSONObject;

public interface AuthProvider {


    public static final String USERNAME_KEY = "username";
    public static final String ACCESS_TOKEN_KEY = "access_token";
    public static final String PASSWORD_KEY = "password";
    public static final String USERID_KEY = "userid";
    public static final String PROVIDER_KEY = "provider";


    void setAuthProvider(String name);

    // attempt to create user
    public void createUser(JSONObject userDetails, AuthResponseHandler handler);

    // attempt to authorize user
    public void authorizeUser(JSONObject userDetails, AuthResponseHandler handler);

    // save this user
    public void saveUser(User user);

    // restore account credentials between sessions
    public void restoreUser(User user);

    // clear user token/email
    public void deauthorizeUser(User user);

}