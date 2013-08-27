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
package com.simperium.android;

import android.content.Context;
import android.content.SharedPreferences;

import com.simperium.client.ClientFactory;
import com.simperium.client.User;
import com.simperium.util.Logger;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;

import com.loopj.android.http.*;

import java.io.UnsupportedEncodingException;

public class AuthClient implements ClientFactory.AuthProvider {

    private static final String AUTH_URL = "https://auth.simperium.com/1";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String API_KEY_HEADER_NAME = "X-Simperium-API-Key";
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";

    private AsyncHttpClient httpClient;
    private String appSecret;
    private String appId;
    private String mAuthProvider;

    public Context context;

    public AuthClient(String appId, String appSecret, AsyncHttpClient httpClient){
        this.appSecret = appSecret;
        this.httpClient = httpClient;
        this.appId = appId;
    }

    public AuthClient(String appId, String appSecret){
        AsyncHttpClient httpClient = new AsyncHttpClient();
        this.appSecret = appSecret;
        this.appId = appId;
        this.httpClient = httpClient;
    }


    public void createUser(User user, User.AuthResponseHandler handler){
        String url = absoluteUrl("create/");
        try {
            httpClient.post(null, url, authHeaders(), user.toHttpEntity(mAuthProvider), JSON_CONTENT_TYPE, user.getCreateResponseHandler(handler));
        } catch (UnsupportedEncodingException e){
            handler.onFailure(user, e, e.getMessage());
        }
    }

    public void authorizeUser(User user, User.AuthResponseHandler handler){
        String url = absoluteUrl("authorize/");
        try {
            httpClient.post(null, url, authHeaders(), user.toHttpEntity(), JSON_CONTENT_TYPE, user.getAuthorizeResponseHandler(handler));
        } catch (UnsupportedEncodingException e){
            handler.onFailure(user, e, e.getMessage());
        }
    }

    private Header[] authHeaders(){
        Header[] headers = new Header[1];
        headers[0] = new BasicHeader(API_KEY_HEADER_NAME, appSecret);
        return headers;
    }

    private String absoluteUrl(String path){
        return String.format("%s/%s/%s", AUTH_URL, appId, path);
    }

    public void setAuthProvider(String authProvider) {
        mAuthProvider = authProvider;
    }

    public void setAccessToken(String token){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, token);
        editor.commit();
    }

    public void clearAccessToken(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(USER_ACCESS_TOKEN_PREFERENCE);
        editor.commit();
    }

    public String getAccessToken(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null);
    }

}

