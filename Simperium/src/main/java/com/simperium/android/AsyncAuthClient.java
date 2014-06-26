package com.simperium.android;

import com.simperium.client.AuthProvider;
import com.simperium.client.AuthResponseHandler;
import com.simperium.client.User;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONObject;

public class AsyncAuthClient implements AuthProvider {

    public static final String TAG = "AsyncAuthClient";

    protected final String mAppId;
    protected final String mSecret;
    protected final AsyncHttpClient mClient;

    public AsyncAuthClient(String appId, String secret, AsyncHttpClient client) {
        mAppId = appId;
        mSecret = secret;
        mClient = client;
    }

    @Override
    public void setAuthProvider(String name) {
        
    }

    // attempt to create user
    @Override
    public void createUser(JSONObject userDetails, AuthResponseHandler handler) {
        
    }

    // attempt to authorize user
    @Override
    public void authorizeUser(JSONObject userDetails, AuthResponseHandler handler) {
        
    }

    // save this user
    @Override
    public void saveUser(User user) {
        
    }

    // restore account credentials between sessions
    @Override
    public void restoreUser(User user) {
        
    }

    // clear user token/email
    @Override
    public void deauthorizeUser(User user) {
        
    }

}