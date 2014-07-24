package com.simperium;

import com.simperium.client.AuthException;
import com.simperium.client.AuthProvider;
import com.simperium.client.AuthResponseHandler;
import com.simperium.client.AuthResponseListener;
import com.simperium.client.Bucket;
import com.simperium.client.BucketNameInvalid;
import com.simperium.client.BucketObject;
import com.simperium.client.BucketSchema;
import com.simperium.client.ChannelProvider;
import com.simperium.client.ClientFactory;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.storage.StorageProvider;
import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.util.AuthUtil;

import java.util.concurrent.Executor;

public class Simperium implements User.StatusChangeListener {

    public static Simperium newClient(String appId, String appSecret, ClientFactory factory){
        simperiumClient = new Simperium(appId, appSecret, factory);
        return simperiumClient;
    }

    public interface OnUserCreatedListener {
        void onUserCreated(User user);
    }

    public static final String VERSION = Version.NUMBER;
    public static final String CLIENT_ID = Version.NAME;
    public static final int SIGNUP_SIGNIN_REQUEST = 1000;  // The request code

    private String appId;
    private String appSecret;

    private User user;
    private User.StatusChangeListener userListener;
    private static Simperium simperiumClient = null;
    private OnUserCreatedListener onUserCreatedListener;

    protected AuthProvider mAuthProvider;
    protected ChannelProvider mChannelProvider;
    protected StorageProvider mStorageProvider;
    protected GhostStorageProvider mGhostStorageProvider;
    protected Executor mExecutor;

    public Simperium(String appId, String appSecret, ClientFactory factory){
        this.appId = appId;
        this.appSecret = appSecret;

        mAuthProvider = factory.buildAuthProvider(appId, appSecret);

        mChannelProvider = factory.buildChannelProvider(appId);

        mStorageProvider = factory.buildStorageProvider();

        mGhostStorageProvider = factory.buildGhostStorageProvider();

        mExecutor = factory.buildExecutor();

        // Logger.log(String.format("Initializing Simperium %s%s", CLIENT_ID, (BuildConfig.DEBUG ? " DEBUG" : "")));
        loadUser();
    }

    public static Simperium getInstance() throws SimperiumNotInitializedException{
    	if(null == simperiumClient)
    		throw new SimperiumNotInitializedException("You must create an instance of Simperium before call this method.");
    	
    	return simperiumClient;
    }

    private void loadUser(){
        user = new User(this);
        mAuthProvider.restoreUser(user);
        if (user.needsAuthorization()) {
            user.setStatus(User.Status.NOT_AUTHORIZED);
        }
    }

    public String getAppId(){
        return appId;
    }

    public User getUser(){
        return user;
    }

    public boolean needsAuthorization(){
        // we don't have an access token yet
        return user.needsAuthorization();
    }

    /**
     * Creates a bucket and starts syncing data and uses the provided
     * Class to instantiate data.
     * 
     * Should only allow one instance of bucketName and the schema should
     * match the existing bucket?
     *
     * @param bucketName the namespace to store the data in simperium
     */
    public <T extends Syncable> Bucket<T> bucket(String bucketName, BucketSchema<T> schema)
    throws BucketNameInvalid {
        return bucket(bucketName, schema, mStorageProvider.createStore(bucketName, schema));
    }

    /**
     * Allow alternate storage mechanisms
     */
    public <T extends Syncable> Bucket<T> bucket(String bucketName, BucketSchema<T> schema, BucketStore<T> storage)
    throws BucketNameInvalid {

        // initialize the bucket
        Bucket<T> bucket = new Bucket<T>(mExecutor, bucketName, schema, user, storage, mGhostStorageProvider);

        // initialize the communication method for the bucket
        Bucket.Channel channel = mChannelProvider.buildChannel(bucket);

        // tell the bucket about the channel
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
    public Bucket<BucketObject> bucket(String bucketName)
    throws BucketNameInvalid {
        return bucket(bucketName, new BucketObject.Schema(bucketName));
    }

    /**
     * Creates a bucket and uses the Schema remote name as the bucket name.
     */
    public <T extends Syncable> Bucket<T> bucket(BucketSchema<T> schema)
    throws BucketNameInvalid {
        return bucket(schema.getRemoteName(), schema);
    }

    public void setAuthProvider(String providerString){
        mAuthProvider.setAuthProvider(providerString);
    }

    public void setOnUserCreatedListener(OnUserCreatedListener listener){
        onUserCreatedListener = listener;
    }

    protected void notifyOnUserCreatedListener(User user){
        if (onUserCreatedListener != null){
            onUserCreatedListener.onUserCreated(user);
        }
    }

    public User createUser(String email, String password, AuthResponseListener listener){
        user.setCredentials(email, password);
        AuthResponseListener wrapper = new AuthResponseListenerWrapper(listener){
            @Override
            public void onSuccess(User user){
                super.onSuccess(user);
                notifyOnUserCreatedListener(user);
            }
        };
        mAuthProvider.createUser(AuthUtil.makeAuthRequestBody(user), new AuthResponseHandler(user, wrapper));
        return user;
    }

    public User authorizeUser(String email, String password, AuthResponseListener listener){
        user.setCredentials(email, password);
        AuthResponseListener wrapper = new AuthResponseListenerWrapper(listener);
        mAuthProvider.authorizeUser(AuthUtil.makeAuthRequestBody(user), new AuthResponseHandler(user, wrapper));
        return user;
    }

    public void deauthorizeUser(){
        user.setAccessToken(null);
        user.setEmail(null);
        mAuthProvider.deauthorizeUser(user);
        user.setStatus(User.Status.NOT_AUTHORIZED);
    }

    public void onUserStatusChange(User.Status status){
        if (userListener != null) {
            userListener.onUserStatusChange(status);
        }
    }

    public User.StatusChangeListener getUserStatusChangeListener() {
        return userListener;
    }

    public void setUserStatusChangeListener(User.StatusChangeListener listener){
        userListener = listener;
    }

    /**
     * Log message with ChannelProvider.LOG_VERBOSE
     */
    public void log(CharSequence message) {
        mChannelProvider.log(ChannelProvider.LOG_VERBOSE, message);
    }

    /**
     * Log message with provided level see ChannelProvider for log level constants.
     */
    public void log(int level, CharSequence message) {
        mChannelProvider.log(level, message);
    }

    private class AuthResponseListenerWrapper implements AuthResponseListener {

        final private AuthResponseListener mListener;

        public AuthResponseListenerWrapper(final AuthResponseListener listener){
            mListener = listener;
        }

        @Override
        public void onSuccess(User user) {
            mAuthProvider.saveUser(user);
            mListener.onSuccess(user);
        }

        @Override
        public void onFailure(User user, AuthException error) {
            mListener.onFailure(user, error);
        }

    }

}
