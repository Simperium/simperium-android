package com.simperium;

import com.loopj.android.http.*;

import com.simperium.storage.StorageProvider;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObject;
import com.simperium.client.BucketSchema;
import com.simperium.client.Channel;
import com.simperium.client.GhostStore;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.client.User.AuthenticationListener;
import com.simperium.client.User.AuthenticationStatus;
import com.simperium.client.User.AuthResponseHandler;
import com.simperium.client.AuthHttpClient;
import com.simperium.client.WebSocketManager;

import com.simperium.util.Logger;
import com.simperium.util.Uuid;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class Simperium implements User.AuthenticationListener {

    public static final String VERSION = "duo-beta";
    public static final String CLIENT_ID = String.format("android-%s", VERSION);
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";
    public static final int SIGNUP_SIGNIN_REQUEST = 1000;  // The request code
    private String appId;
    private String appSecret;

    protected AsyncHttpClient httpClient;
    protected AuthHttpClient authClient;
    protected WebSocketManager socketManager;

    private User user;
    private Context context;
    private AuthenticationListener authenticationListener;
    private StorageProvider storageProvider;
    private static Simperium simperiumClient = null;
	private GhostStore ghostStore;
        
    public Simperium(String appId, String appSecret, Context context, StorageProvider storageProvider){
        this(appId, appSecret, context, storageProvider, null);
    }

    public Simperium(String appId, String appSecret, Context context, StorageProvider storageProvider, AuthenticationListener authenticationListener){
        this.appId = appId;
        this.appSecret = appSecret;
        this.context = context;
        httpClient = new AsyncHttpClient();
        httpClient.setUserAgent(CLIENT_ID);
        authClient = new AuthHttpClient(appId, appSecret, httpClient);
        socketManager = new WebSocketManager(appId, String.format("%s-%s", CLIENT_ID, Uuid.uuid().substring(0,6)));
        this.authenticationListener = authenticationListener;
        this.storageProvider = storageProvider;
		ghostStore = new GhostStore(context);
        loadUser();
        Logger.log(String.format("Initializing Simperium %s", CLIENT_ID));
        simperiumClient = this;
    }
    
    
    public static Simperium getInstance() throws SimperiumNotInitializedException{
    	if(null == simperiumClient)
    		throw new SimperiumNotInitializedException("You must create an instance of Simperium before call this method.");
    	
    	return simperiumClient;
    }

    public void connect(){
        socketManager.connect();
    }

    public void disconnect(){
        socketManager.disconnect();
    }

    public boolean isConnected(){
        return socketManager.isConnected();
    }

    public boolean isConnecting(){
        return socketManager.isConnecting();
    }

    public boolean isDisconnected(){
        return socketManager.isDisconnected();
    }

    private void loadUser(){
        user = new User(this);
        String token = getUserAccessToken();
        if (token != null) {
            user.setAccessToken(token);
            user.setAuthenticationStatus(AuthenticationStatus.AUTHENTICATED);
        } else {
            user.setAuthenticationStatus(AuthenticationStatus.NOT_AUTHENTICATED);
        }
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

    /**
     * Creates a bucket and starts syncing data and uses the provided
     * Class to instantiate data.
     * 
     * Should only allow one instance of bucketName and the scheme should
     * match the existing bucket.
     *
     * @param bucketName the namespace to store the data in simperium
     */
    public <T extends Syncable> Bucket<T> bucket(String bucketName, BucketSchema<T> schema){
        StorageProvider.BucketStore<T> storage = storageProvider.createStore();
        Bucket<T> bucket = new Bucket<T>(bucketName, schema, user, storageProvider, ghostStore);
        Channel<T> channel = socketManager.createChannel(context, bucket, user);
        bucket.setChannel(channel);
        return bucket;
    }
    /**
     * Creates a bucket and starts syncing data. Users the generic BucketObject for
     * serializing and deserializing data
     *
     * @param bucketName namespace to store the data in simperium
     */
    public Bucket<BucketObject> bucket(String bucketName){
        return bucket(bucketName, new BucketObject.Schema(bucketName));
    }
    /**
     * Creates a bucket and uses the Schema remote name as the bucket name.
     */
    public <T extends Syncable> Bucket<T> bucket(BucketSchema<T> schema){
        return bucket(schema.getRemoteName(), schema);
    }

    public User createUser(String email, String password, AuthResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.createUser(user, handler);
    }

    public User authorizeUser(String email, String password, AuthResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.authorizeUser(user, handler);
    }

    public boolean deAuthorizeUser(){
    	user.setAccessToken(null);
    	user.setAuthenticationStatus(AuthenticationStatus.NOT_AUTHENTICATED);
    	clearUserAccessToken();
    	return true;
    }

    public void onAuthenticationStatusChange(AuthenticationStatus status){

        switch (status) {
            case AUTHENTICATED:
            // Start up the websocket
            // save the key
            saveUserAccessToken();
            socketManager.connect();
            break;
            case NOT_AUTHENTICATED:
            clearUserAccessToken();
            // Disconnect the websocket
            socketManager.disconnect();
            break;
            case UNKNOWN:
            // we haven't tried to auth yet or the socket is disconnected
            break;
        }
        if (authenticationListener != null) {
            authenticationListener.onAuthenticationStatusChange(status);
        }
    }

    private SharedPreferences.Editor getPreferenceEditor(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences.edit();
    }

    private boolean clearUserAccessToken(){
        SharedPreferences.Editor editor = getPreferenceEditor();
        editor.remove(USER_ACCESS_TOKEN_PREFERENCE);
        return editor.commit();
    }

    private boolean saveUserAccessToken(){
        String token = user.getAccessToken();
        SharedPreferences.Editor editor = getPreferenceEditor();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, token);
        return editor.commit();
    }

    private String getUserAccessToken(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String token = preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null);
        return token;
    }

	public AuthenticationListener getAuthenticationListener() {
		return authenticationListener;
	}

	public void setAuthenticationListener(
			AuthenticationListener authenticationListener) {
		this.authenticationListener = authenticationListener;
	}

}
