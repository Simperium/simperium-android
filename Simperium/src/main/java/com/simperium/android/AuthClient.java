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

import com.simperium.client.AuthProvider;
import com.simperium.client.User;
import com.simperium.util.Logger;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;

import com.loopj.android.http.*;

import java.io.UnsupportedEncodingException;

import org.json.JSONObject;

public class AuthClient implements AuthProvider {

    private static final String AUTH_URL = "https://auth.simperium.com/1";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String API_KEY_HEADER_NAME = "X-Simperium-API-Key";
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";
    public static final String USER_EMAIL_PREFERENCE = "user-email";

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

    private Header[] authHeaders(){
        Header[] headers = new Header[1];
        headers[0] = new BasicHeader(API_KEY_HEADER_NAME, appSecret);
        return headers;
    }

    private String absoluteUrl(String path){
        return String.format("%s/%s/%s", AUTH_URL, appId, path);
    }

    @Override
    public void setAuthProvider(String authProvider) {
        mAuthProvider = authProvider;
    }

    @Override
    public void createUser(User user, User.AuthResponseHandler handler){
        String url = absoluteUrl("create/");
        User.AuthResponseHandler saveOnSuccessHandler = wrapHandler(user, handler);
        try {
            httpClient.post(null, url, authHeaders(), user.toHttpEntity(mAuthProvider), JSON_CONTENT_TYPE, user.getCreateResponseHandler(saveOnSuccessHandler));
        } catch (UnsupportedEncodingException e){
            handler.onFailure(user, e, e.getMessage());
        }
    }

    @Override
    public void authorizeUser(User user, User.AuthResponseHandler handler){
        String url = absoluteUrl("authorize/");
        User.AuthResponseHandler saveOnSuccessHandler = wrapHandler(user, handler);
        try {
            httpClient.post(null, url, authHeaders(), user.toHttpEntity(), JSON_CONTENT_TYPE, user.getAuthorizeResponseHandler(saveOnSuccessHandler));
        } catch (UnsupportedEncodingException e){
            handler.onFailure(user, e, e.getMessage());
        }
    }

    @Override
    public void restoreUser(User user){
        SharedPreferences preferences = getPreferences();
        user.setAccessToken(preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null));
        user.setEmail(preferences.getString(USER_EMAIL_PREFERENCE, null));
    }

    @Override
    public void deauthorizeUser(User user){
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.remove(USER_ACCESS_TOKEN_PREFERENCE);
        editor.remove(USER_EMAIL_PREFERENCE);
        editor.commit();
    }

    protected void saveUser(User user){
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, user.getAccessToken());
        editor.putString(USER_EMAIL_PREFERENCE, user.getEmail());
        editor.commit();
    }

    private SharedPreferences getPreferences(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences;
    }

    // all of this so we can persist the user token
    private User.AuthResponseHandler wrapHandler(final User user, final User.AuthResponseHandler handler){
        return new User.AuthResponseHandler(){

            @Override
            public void onSuccess(User user){
                saveUser(user);
                handler.onSuccess(user);
            }

            @Override
            public void onInvalid(User user, Throwable error, JSONObject validationErrors){
                handler.onInvalid(user, error, validationErrors);
            }

            @Override
            public void onFailure(User user, Throwable error, String message){
                handler.onFailure(user, error, message);
            }

        };
    }

}

