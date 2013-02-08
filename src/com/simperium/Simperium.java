package com.simperium;

import com.loopj.android.http.*;
import com.loopj.android.http.JsonHttpResponseHandler;

import android.util.Log;

import com.simperium.user.*;
import com.simperium.WebSocketManager;

import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;

import com.simperium.BucketObject;

public class Simperium {

    public static final String HTTP_USER_AGENT = "android-1.0";
    private String appId;
    private String appSecret;

    public static final String TAG = "Simperium";
    protected AsyncHttpClient httpClient;
    protected AuthHttpClient authClient;
    protected WebSocketManager socketManager;
    
    public Simperium(String appId, String appSecret){
        this.appId = appId;
        this.appSecret = appSecret;
        httpClient = new AsyncHttpClient();
        httpClient.setUserAgent(HTTP_USER_AGENT);
        authClient = new AuthHttpClient(appId, appSecret, httpClient);
        socketManager = new WebSocketManager(appId);
    }
    
    public String getAppId(){
        return appId;
    }
    
    /*
     * Registers a bucket and starts picking up and applying changes
     * by creating a Channel that communicates over the websocket.
     * 
     * 
     *
     */
    public Bucket bucket(String bucketName, User user){
        // TODO: cache the bucket by user and bucketName and return the
        // same bucket if asked for again
        Bucket bucket = new Bucket(bucketName, user);
        // start the channel here?
        Channel channel = socketManager.createChannel(bucket, user);
        return bucket;
    }
    
    /**
     * User managment methods
     *
     *
     */
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
