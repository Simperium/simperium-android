package com.simperium;

import com.loopj.android.http.*;
import com.loopj.android.http.JsonHttpResponseHandler;

import android.util.Log;
import android.content.Context;


import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;

import com.simperium.BucketObject;

public class Simperium implements User.AuthenticationListener {

    public static final String HTTP_USER_AGENT = "android-1.0";
    private String appId;
    private String appSecret;

    public static final String TAG = "Simperium";
    protected AsyncHttpClient httpClient;
    protected AuthHttpClient authClient;
    protected WebSocketManager socketManager;
    
    private User user;
    private Context context;
    private User.AuthenticationListener authenticationListener;
    
    public Simperium(String appId, String appSecret, Context context){
        this(appId, appSecret, context, null);
    }
    
    public Simperium(String appId, String appSecret, Context context, User.AuthenticationListener authenticationListener){
        this.appId = appId;
        this.appSecret = appSecret;
        this.context = context;
        httpClient = new AsyncHttpClient();
        httpClient.setUserAgent(HTTP_USER_AGENT);
        authClient = new AuthHttpClient(appId, appSecret, httpClient);
        socketManager = new WebSocketManager(appId);
        this.authenticationListener = authenticationListener;
        loadUser();
    }
    
    private void loadUser(){
        // TODO: store the auth token in SharedPreferences
        user = new User(this);
        // if the user has an auth token, set the token 
        user.setAuthenticationStatus(User.AuthenticationStatus.NOT_AUTHENTICATED);
    }
    
    public String getAppId(){
        return appId;
    }
    
    public User getUser(){
        return user;
    }
    
    public boolean needsAuthentication(){
        // we don't have an access token yet
        return user.needsAuthentication();
    }
    
    public Bucket bucket(String bucketName){
        // TODO: cache the bucket by user and bucketName and return the
        // same bucket if asked for again
        Bucket bucket = new Bucket(bucketName, user);
        // start the channel here?
        Channel channel = socketManager.createChannel(bucket, user);
        return bucket;
    }
        
    public User createUser(String email, String password, UserResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.createUser(user, handler);
    }
    
    public User authorizeUser(String email, String password, UserResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.authorizeUser(user, handler);
    }
    
    public static final void log(String msg){
        Log.i(TAG, msg);
    }
    
    public void onAuthenticationStatusChange(User.AuthenticationStatus status){
        Simperium.log("User auth has changed");
        switch (status) {
            case AUTHENTICATED:
            socketManager.connect();
            break;
            case NOT_AUTHENTICATED:
            socketManager.disconnect();
            break;
            case UNKNOWN:
            // we haven't tried to auth yet
            break;
        }
        if (authenticationListener != null) {
            authenticationListener.onAuthenticationStatusChange(status);
        }
    }
                
}
