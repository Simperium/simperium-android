package com.simperium.android;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.simperium.client.AuthException;
import com.simperium.client.AuthProvider;
import com.simperium.client.AuthResponseHandler;
import com.simperium.client.User;
import com.simperium.util.AuthUtil;
import com.simperium.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class VolleyAuthClient implements AuthProvider {

    public static final String TAG = "Simperium.VolleyAuthClient";

    private static final String AUTH_URL = "https://auth.simperium.com/1";
    private static final String CREATE_PATH = "create/";
    private static final String AUTHORIZE_PATH = "authorize/";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String API_KEY_HEADER_NAME = "X-Simperium-API-Key";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";
    public static final String USER_EMAIL_PREFERENCE = "user-email";

    private String mAppSecret;
    private String mAppId;
    private String mAuthProvider;
    private Context mContext;

    protected RequestQueue mQueue;

    public VolleyAuthClient(String appId, String appSecret, Context context) {
        this(appId, appSecret, context, Volley.newRequestQueue(context));
        mQueue.start();
    }

    public VolleyAuthClient(String appId, String appSecret, Context context, RequestQueue requestQueue){
        mAppId = appId;
        mAppSecret = appSecret;
        mQueue = requestQueue;
        mContext = context;
    }

    public String absoluteUrl(String path){
        return String.format("%s/%s/%s", AUTH_URL, mAppId, path);
    }

    @Override
    public void setAuthProvider(String authProvider) {
        mAuthProvider = authProvider;
    }

    @Override
    public void createUser(JSONObject userDetails, AuthResponseHandler handler){
        try {
            if(mAuthProvider != null) userDetails.put(AuthUtil.PROVIDER_KEY, mAuthProvider);
        } catch (JSONException e) {
            Logger.log(TAG, "Failed to set auth provider field", e);
        }
        sendRequest(CREATE_PATH, userDetails, handler);
    }

    @Override
    public void authorizeUser(JSONObject userDetails, AuthResponseHandler handler){
        sendRequest(AUTHORIZE_PATH, userDetails, handler);
    }

    @Override
    public void restoreUser(User user) {
        SharedPreferences preferences = AndroidClient.sharedPreferences(mContext);
        user.setAccessToken(preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null));
        user.setEmail(preferences.getString(USER_EMAIL_PREFERENCE, null));
    }

    @Override
    public void deauthorizeUser(User user) {
        SharedPreferences.Editor editor = AndroidClient.sharedPreferences(mContext).edit();
        editor.remove(USER_ACCESS_TOKEN_PREFERENCE);
        editor.remove(USER_EMAIL_PREFERENCE);
        editor.commit();
    }

    @Override
    public void saveUser(User user) {
        SharedPreferences.Editor editor = AndroidClient.sharedPreferences(mContext).edit();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, user.getAccessToken());
        editor.putString(USER_EMAIL_PREFERENCE, user.getEmail());
        editor.commit();
    }

    protected void sendRequest(String path, JSONObject body, final AuthResponseHandler handler){

        Response.Listener<JSONObject> listener = new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response){
                handler.onResponse(response);
            }

        };
        
        Response.ErrorListener errorListener = new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error){
                if (error.networkResponse != null) {
                    handler.onError(
                        AuthException.exceptionForStatusCode(error.networkResponse.statusCode));
                } else {
                    handler.onError(AuthException.defaultException());
                }
            }

        };

        AuthRequest request = new AuthRequest(path, body, listener, errorListener);
        mQueue.add(request);
    }

    private class AuthRequest extends JsonObjectRequest {

        protected AuthRequest(String path, JSONObject body, Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener){
            super(Request.Method.POST, absoluteUrl(path), body, listener, errorListener);
        }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            Map headers = new HashMap<String, String>(1);
            headers.put(API_KEY_HEADER_NAME, mAppSecret);
            return headers;
        }

    }

}