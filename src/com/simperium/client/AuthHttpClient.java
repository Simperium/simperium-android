/**
 * The HTTP client used to access the auth.simperium.com API. This provides apps a
 * simple way to create and authenticate users.
 *
 * An app most likely will never need to access it directly. They will use the Simperium
 * API's to authenticate/create users.
 *
 *    simperium.createUser("user@example.com", "super-secret", new UserResponseHandler(){
 *      public void onSuccess(){
 *          // hooray
 *      }
 *    })
 *
 * Apps can also provide a User.AuthenticationListener to the Simperium constructor so they
 * can provide feedback to the user in a global way. See User for more info.
 *
 */
package com.simperium.client;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import com.loopj.android.http.*;

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


    public User createUser(User user, User.AuthResponseHandler handler){
        String url = absoluteUrl("create/");
        Simperium.log(String.format("Requesting: %s", url));
        httpClient.post(null, url, authHeaders(), user.toHttpEntity(), JSON_CONTENT_TYPE, user.getCreateResponseHandler(handler));
        return user;
    }

    public User authorizeUser(User user, User.AuthResponseHandler handler){
        String url = absoluteUrl("authorize/");
        Simperium.log(String.format("Requesting: %s", url));
        httpClient.post(null, url, authHeaders(), user.toHttpEntity(), JSON_CONTENT_TYPE, user.getAuthorizeResponseHandler(handler));
        return user;
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

