package com.simperium;

import java.util.HashMap;
import java.util.Map;

import com.codebutler.android_websockets.*;
import com.loopj.android.http.*;
import com.loopj.android.http.JsonHttpResponseHandler;

import android.util.Log;

import com.simperium.user.*;

public class Simperium {

    private static final String HTTP_USER_AGENT = "Simperium-Android (alpha)";
    private String appId;
    private String appSecret;

    public static final String TAG = "Simperium";
    protected AsyncHttpClient httpClient;
    protected AuthHttpClient authClient;
    
    public Simperium(String appId, String appSecret){
        this.appId = appId;
        this.appSecret = appSecret;
        httpClient = new AsyncHttpClient();
        httpClient.setUserAgent(HTTP_USER_AGENT);
        authClient = new AuthHttpClient(appId, appSecret, httpClient);
        
    }
    
    public User createUser(User user, UserResponseHandler handler){
        return authClient.createUser(user, handler);
    }
    
    public User createUser(String username, String password, UserResponseHandler handler){
        return authClient.createUser(username, password, handler);
    }
    public User authorizeUser(User user, UserResponseHandler handler){
        return authClient.authorizeUser(user, handler);
    }
    public User authorizeUser(String username, String password, UserResponseHandler handler){
        return authClient.authorizeUser(username, password, handler);
    }
    
    public static final void log(String msg){
        Log.i(TAG, msg);
    }
                
}
