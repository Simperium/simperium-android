package com.simperium.user;

import com.simperium.user.UserResponseHandler;

import com.simperium.Simperium;

import com.loopj.android.http.*;

import org.apache.http.Header;
import org.apache.http.client.HttpResponseException;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import android.util.Log;

public class AuthHttpClient {
    
    private static final String AUTH_URL = "https://auth.simperium.com/1";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String API_KEY_HEADER_NAME = "X-Simperium-API-Key";
    
    private AsyncHttpClient httpClient;
    private String appSecret;
    private String appId;
    
    
    public AuthHttpClient(String appId, String appSecret, AsyncHttpClient httpClient){
        this.appSecret = appSecret;
        this.httpClient = httpClient;
        this.appId = appId;
    }
    
    public AuthHttpClient(String appId, String appSecret){
        AsyncHttpClient httpClient = new AsyncHttpClient();
        this.appSecret = appSecret;
        this.appId = appId;
        this.httpClient = httpClient;
    }
    
    
    public User createUser(User user, UserResponseHandler handler){
        String url = absoluteUrl("create/");
        Simperium.log(String.format("Requesting: %s", url));
        httpClient.post(null, url, authHeaders(), user.toHttpEntity(), JSON_CONTENT_TYPE, user.getCreateResponseHandler(handler));
        return user;
    }
    
    public User createUser(String username, String password, UserResponseHandler handler){
        User user = new User(username, password);
        return createUser(user, handler);
    }
    
    public User authorizeUser(User user, UserResponseHandler handler){
        String url = absoluteUrl("authorize/");
        Simperium.log(String.format("Requesting: %s", url));
        httpClient.post(null, url, authHeaders(), user.toHttpEntity(), JSON_CONTENT_TYPE, user.getAuthorizeResponseHandler(handler));
        return user;
    }
    
    public User authorizeUser(String username, String password, UserResponseHandler handler){
        // make an HTTP request to authorize the user
        User user = new User(username, password);
        return authorizeUser(user, handler);
    }
    
    private Header[] authHeaders(){
        Header[] headers = new Header[1];
        headers[0] = new BasicHeader(API_KEY_HEADER_NAME, appSecret);
        return headers;
    }
    
    private String absoluteUrl(String path){
        return String.format("%s/%s/%s", AUTH_URL, appId, path);
    }
    
    
}

