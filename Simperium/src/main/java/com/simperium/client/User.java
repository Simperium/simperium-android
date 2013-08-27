/**
 * User is used to determine authentication status for the client. Applications should
 * interact with the User object using the Simperium object's methods:
 *
 *     simperium.createUser( ... ); // register new user
 *     simperium.authorizeUser( ... ); // authorizes an existing user
 *
 * Applications can provide a User.AuthenticationListener to the Simperium object to
 * detect a change in the user's status.
 */
package com.simperium.client;

import com.simperium.util.Logger;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;

public class User {
    /**
     * Applications can register a global authentication listener to get notified when a user's
     * authenticated status has changed.
     *
     *     Simperium simperium = new Simperium(appId, appSecret, appContext, new User.AuthenticationListener(){
     *       public void onAuthenticationStatusChange(User.AuthenticationStatus status){
     *          // Prompt user to log in or show they're offline
     *       }
     *     });
     */
    public interface AuthenticationListener {
        void onAuthenticationStatusChange(AuthenticationStatus authorized);
    }
    /**
     * Determines a user's network status with Simperium:
     *   - AUTHENTICATED: user has an access token and is connected to Simperium
     *   - NOT_AUTHENTICATED: user does not have a valid access token. Create or auth the user
     *   - UKNOWN: User objects start in this state and then transitions to AUTHENTICATED or
     *             NOT_AUTHENTICATED. Also the state given when the Simperium is not reachable.
     */
    public enum AuthenticationStatus {
        AUTHENTICATED, NOT_AUTHENTICATED, UNKNOWN
    }
    /**
     * For use with Simperium.createUser and Simperium.authorizeUser
     */
    public interface AuthResponseHandler {
        public void onSuccess(User user);
        public void onInvalid(User user, Throwable error, JSONObject validationErrors);
        public void onFailure(User user, Throwable error, String message);
    }

    public static final String USERNAME_KEY = "username";
    public static final String ACCESS_TOKEN_KEY = "access_token";
    public static final String PASSWORD_KEY = "password";
    public static final String USERID_KEY = "userid";
    public static final String PROVIDER_KEY = "provider";

    private String email;
    private String password;
    private String userId;
    private String accessToken;
    private AuthenticationStatus authenticationStatus = AuthenticationStatus.UNKNOWN;
    private AuthenticationListener listener;

    public User(){
        this(null, null, null);
    }
    // a user that hasn't been logged in
    public User(AuthenticationListener listener){
        this(null, null, listener);
    }

    public User(String email, AuthenticationListener listener){
        this(email, null, listener);
    }

    public User(String email, String password, AuthenticationListener listener){
        this.email = email;
        this.password = password;
        this.listener = listener;
    }

    public AuthenticationStatus getAuthenticationStatus(){
        return authenticationStatus;
    }

    public void setAuthenticationListener(AuthenticationListener authListener){
        listener = authListener;
    }

    public AuthenticationListener getAuthenticationListener(){
        return listener;
    }

    public void setAuthenticationStatus(AuthenticationStatus authenticationStatus){
        if (this.authenticationStatus != authenticationStatus) {
            this.authenticationStatus = authenticationStatus;
            if (this.listener != null) {
                listener.onAuthenticationStatusChange(this.authenticationStatus);                
            }
        }
    }

    // check if we have an access token
    public boolean needsAuthentication(){
        return accessToken == null;
    }

    public boolean hasAccessToken(){
        return accessToken != null;
    }

    public void setCredentials(String email, String password){
        setEmail(email);
        setPassword(password);
    }

    public String getEmail(){
        return email;
    }

    public void setEmail(String email){
        this.email = email;
    }

    public void setPassword(String password){
        this.password = password;
    }

    public String getUserId(){
        return userId;
    }

    public String getAccessToken(){
        return accessToken;
    }

    public void setAccessToken(String token){
        this.accessToken = token;
    }

    public String toString(){
        return toJSONString();
    }

    public String toJSONString(){
        return new JSONObject(toMap()).toString();
    }

    private Map<String,String> toMap(){
        HashMap<String,String> fields = new HashMap<String,String>();
        fields.put(USERNAME_KEY, email);
        fields.put(PASSWORD_KEY, password);
        return fields;
    }

    public HttpEntity toHttpEntity() throws UnsupportedEncodingException {
        JSONObject json = new JSONObject(toMap());
        return new StringEntity(json.toString());

    }

    public HttpEntity toHttpEntity(String authProvider) throws UnsupportedEncodingException {
        if (authProvider == null){
            return toHttpEntity();
        }
        List<NameValuePair> parameters = new ArrayList<NameValuePair>(3);
        parameters.add(new BasicNameValuePair(USERNAME_KEY, email));
        parameters.add(new BasicNameValuePair(PASSWORD_KEY, password));
        parameters.add(new BasicNameValuePair(PROVIDER_KEY, authProvider));
        return new UrlEncodedFormEntity(parameters, HTTP.UTF_8);
    }

    public AsyncHttpResponseHandler getCreateResponseHandler(final User.AuthResponseHandler handler){
        // returns an AsyncHttpResponseHandlert
        final User user = this;
        return new JsonHttpResponseHandler(){
            @Override
            public void onSuccess(int statusCode, JSONObject response){
                // parse the response to JSON
                Logger.log(String.format("Success: %s", response));
                // user was created, notify of a new user
                try {
                    userId = response.getString(USERID_KEY);
                    accessToken = response.getString(ACCESS_TOKEN_KEY);
                } catch(JSONException error){
                    handler.onFailure(user, error, response.toString());
                    return;
                }
                setAuthenticationStatus(AuthenticationStatus.AUTHENTICATED);
                handler.onSuccess(user);
            }
            @Override
            public void onFailure(Throwable error, JSONObject response){
                Logger.log(String.format("Error: %s", error));
                Logger.log(String.format("Reponse: %s", response));
                handler.onInvalid(user, error, response);
            }
            @Override
            public void onFailure(Throwable error, String response){
                Logger.log(String.format("Error: %s", error));
                Logger.log(String.format("Reponse: %s", response));
                handler.onFailure(user, error, response);
            }
        };
    }

    public AsyncHttpResponseHandler getAuthorizeResponseHandler(final User.AuthResponseHandler handler){
        final User user = this;
        return new JsonHttpResponseHandler(){
            @Override
            public void onSuccess(int statusCode, JSONObject response){
                // parse the response to JSON
                Logger.log(String.format("Success: %s", response));
                // user was created, notify of a new user
                try {
                    userId = response.getString(USERID_KEY);
                    accessToken = response.getString(ACCESS_TOKEN_KEY);
                } catch(JSONException error){
                    handler.onFailure(user, error, response.toString());
                    return;
                }
                setAuthenticationStatus(AuthenticationStatus.AUTHENTICATED);
                handler.onSuccess(user);
            }
            @Override
            public void onFailure(Throwable error, JSONObject response){
                Logger.log(String.format("Error: %s", error));
                Logger.log(String.format("Reponse: %s", response));
                handler.onInvalid(user, error, response);
            }
            @Override
            public void onFailure(Throwable error, String response){
                Logger.log(String.format("Error: %s", error));
                Logger.log(String.format("Reponse: %s", response));
                handler.onFailure(user, error, response);
            }
        };
    }
    
    public AsyncHttpResponseHandler getUpdateResponseHandler(final User.AuthResponseHandler handler){
        final User user = this;
        return new JsonHttpResponseHandler(){
            @Override
            public void onSuccess(int statusCode, String response){
            }
            @Override
            public void onFailure(Throwable error, JSONObject response){
            }
            @Override
            public void onFailure(Throwable error, String response){
            }
        };
    }
}
