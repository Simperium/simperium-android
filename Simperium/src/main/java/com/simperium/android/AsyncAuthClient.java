package com.simperium.android;

import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient.JSONObjectCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;
import com.koushikdutta.async.parser.JSONObjectParser;
import com.simperium.BuildConfig;
import com.simperium.client.AuthException;
import com.simperium.client.AuthProvider;
import com.simperium.client.AuthResponseHandler;
import com.simperium.client.User;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

public class AsyncAuthClient implements AuthProvider {

    public static final String TAG = "Simperium.AsyncAuthClient";

    private static final String AUTH_URL = "https://auth.simperium.com/1";
    private static final String CREATE_PATH = "create/";
    private static final String AUTHORIZE_PATH = "authorize/";
    private static final String API_KEY_HEADER_NAME = "X-Simperium-API-Key";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";
    public static final String USER_EMAIL_PREFERENCE = "user-email";
    public static final String PROVIDER_KEY = "provider";

    protected final String mAppId;
    protected final String mSecret;
    protected final AsyncHttpClient mClient;
    private final Context mContext;
    private String mAuthProvider;

    public AsyncAuthClient(Context context, String appId, String secret, AsyncHttpClient client) {
        mContext = context;
        mAppId = appId;
        mSecret = secret;
        mClient = client;
    }

    @Override
    public void setAuthProvider(String name) {
        mAuthProvider = name;
    }

    // attempt to create user
    @Override
    public void createUser(JSONObject userDetails, AuthResponseHandler handler) {
        try {
            if (mAuthProvider != null) userDetails.put(PROVIDER_KEY, mAuthProvider);            
        } catch (JSONException e) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Unable to add auth provider");
            }
        }
        sendRequest(CREATE_PATH, userDetails, handler);
    }

    // attempt to authorize user
    @Override
    public void authorizeUser(JSONObject userDetails, AuthResponseHandler handler) {
        sendRequest(AUTHORIZE_PATH, userDetails, handler);
    }

    // save this user
    @Override
    public void saveUser(User user) {
        SharedPreferences.Editor editor = Simperium.sharedPreferences(mContext).edit();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, user.getAccessToken());
        editor.putString(USER_EMAIL_PREFERENCE, user.getEmail());
        editor.commit();
    }

    // restore account credentials between sessions
    @Override
    public void restoreUser(User user) {
        SharedPreferences preferences = Simperium.sharedPreferences(mContext);
        user.setAccessToken(preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null));
        user.setEmail(preferences.getString(USER_EMAIL_PREFERENCE, null));
    }

    // clear user token/email
    @Override
    public void deauthorizeUser(User user) {
        SharedPreferences.Editor editor = Simperium.sharedPreferences(mContext).edit();
        editor.remove(USER_ACCESS_TOKEN_PREFERENCE);
        editor.remove(USER_EMAIL_PREFERENCE);
        editor.commit();
    }

    public Uri buildUri(String path) {
        return Uri.parse(AUTH_URL + "/" + mAppId + "/" + path);
    }

    public AsyncHttpPost buildRequest(String path, JSONObject body) {
        Uri uri = buildUri(path);
        AsyncHttpPost request = new AsyncHttpPost(uri);
        request.setBody(new JSONObjectBody(body));
        request.setHeader(API_KEY_HEADER_NAME, mSecret);
        return request;        
    }

    private void sendRequest(String path, JSONObject body, final AuthResponseHandler handler) {
        
        mClient.execute(buildRequest(path, body), new JSONObjectParser(), new JSONObjectCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, JSONObject result) {

                int responseCode = AuthException.ERROR_STATUS_CODE;

                if (source != null) {
                    ResponseHeaders headers = source.getHeaders();
                    RawHeaders rawHeaders = headers.getHeaders();
                    responseCode = rawHeaders.getResponseCode();
                }

                if (e != null) {
                    Log.d(TAG, "Exception: " + e + " | " + e.getMessage());
                    handler.onError(AuthException.exceptionForStatusCode(responseCode));
                    return;
                }

                if (responseCode == 200) {
                    handler.onResponse(result);
                    return;
                }

                handler.onError(AuthException.exceptionForStatusCode(responseCode));

            }
        });
    }

}
