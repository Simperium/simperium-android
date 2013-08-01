package com.simperium;

import com.loopj.android.http.*;

import com.simperium.storage.StorageProvider;
import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.storage.PersistentStore;

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
import com.simperium.client.FileQueueSerializer;

import com.simperium.util.Logger;
import com.simperium.util.Uuid;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

public class Simperium implements User.AuthenticationListener {

    public interface OnUserCreatedListener {
        void onUserCreated(User user);
    }

    public static final String VERSION = "duo-beta";
    public static final String CLIENT_ID = String.format("android-%s", VERSION);
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";
    public static final int SIGNUP_SIGNIN_REQUEST = 1000;  // The request code
    public static final String DEFAULT_DATABASE_NAME = "simperium-store";

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
    private Channel.Serializer channelSerializer;
    private OnUserCreatedListener onUserCreatedListener;
    
    public Simperium(String appId, String appSecret, Context context){
        this(appId, appSecret, context, new PersistentStore(context.openOrCreateDatabase(DEFAULT_DATABASE_NAME, 0, null)), null);
    }

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
        channelSerializer = new FileQueueSerializer(context);
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
        BucketStore<T> storage = storageProvider.createStore(bucketName, schema);
        Bucket<T> bucket = new Bucket<T>(bucketName, schema, user, storage, ghostStore);
        Bucket.ChannelProvider<T> channel = socketManager.createChannel(bucket, channelSerializer);
        bucket.setChannel(channel);
        storage.prepare(bucket);
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

    public void setAuthProvider(String providerString){
        authClient.setAuthProvider(providerString);
    }

    protected void setOnUserCreatedListener(OnUserCreatedListener listener){
        onUserCreatedListener = listener;
    }

    protected void notifyOnUserCreatedListener(User user){
        if (onUserCreatedListener != null){
            onUserCreatedListener.onUserCreated(user);
        }
    }

    public User createUser(String email, String password, final AuthResponseHandler handler){
        user.setCredentials(email, password);
        AuthResponseHandler wrapper = new AuthResponseHandlerWrapper(handler){
            @Override
            public void onSuccess(User user){
                notifyOnUserCreatedListener(user);
            }
        };
        return authClient.createUser(user, wrapper);
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
        return preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null);
    }

	public AuthenticationListener getAuthenticationListener() {
		return authenticationListener;
	}

    public void setAuthenticationListener(AuthenticationListener listener){
        authenticationListener = listener;
    }

    private class AuthResponseHandlerWrapper implements AuthResponseHandler {

        final private AuthResponseHandler handler;

        public AuthResponseHandlerWrapper(final AuthResponseHandler handler){
            this.handler = handler;
        }

        @Override
        public void onSuccess(User user) {
            handler.onSuccess(user);
        }

        @Override
        public void onInvalid(User user, Throwable error, JSONObject validationErrors) {
            handler.onInvalid(user, error, validationErrors);
        }

        @Override
        public void onFailure(User user, Throwable error, String message) {
            handler.onFailure(user, error, message);
        }
    }
}
