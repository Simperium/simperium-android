package com.simperium.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import com.koushikdutta.async.http.AsyncHttpClient;

import org.thoughtcrime.ssl.pinning.PinningTrustManager;
import org.thoughtcrime.ssl.pinning.SystemKeyStore;

import com.simperium.BuildConfig;
import com.simperium.SimperiumException;
import com.simperium.Version;
import com.simperium.client.AuthProvider;
import com.simperium.client.AuthException;
import com.simperium.client.AuthResponseHandler;
import com.simperium.client.AuthResponseListener;
import com.simperium.client.BucketNameInvalid;
import com.simperium.client.BucketObject;
import com.simperium.client.BucketSchema;
import com.simperium.client.ChannelProvider;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.util.AuthUtil;
import com.simperium.util.Uuid;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javax.net.ssl.TrustManager;

import android.util.Log;

/**
 * Refactoring as much of the android specific components of the client
 * and decoupling different parts of the API.
 */
public class Simperium implements User.StatusChangeListener {

    public interface OnUserCreatedListener {
        void onUserCreated(User user);
    }

    public interface Client {
        public Executor buildExecutor();
        public AuthProvider buildAuthProvider();
        public ChannelProvider buildChannelProvider();
        public StorageProvider buildStorageProvider();
        public GhostStorageProvider buildGhostStorageProvider();
    }

    public static final String TAG = "Simperium";
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String DEFAULT_DATABASE_NAME = "simperium-store";
    public static final String SESSION_ID_PREFERENCE = "simperium-session-id";

    public static final String WEBSOCKET_URL = "https://api.simperium.com/sock/1/%s/websocket";
    public static final String USER_AGENT_HEADER = "User-Agent";

    public static final String VERSION = Version.NUMBER;
    public static final String CLIENT_ID = Version.NAME;
    public static final int SIGNUP_SIGNIN_REQUEST = 1000;

    protected static Simperium sSimperium;

    public static Simperium getInstance()
    throws SimperiumException {
        if (null == sSimperium) {
            throw new SimperiumException("Simperium has not been initialized");
        }
        return sSimperium;
    }

    public static Simperium initializeClient(Context context, String appId, String appSecret) {
        Client client = new ClientImpl(context, appId, appSecret);
        return initializeClient(client);
    }

    public static Simperium initializeClient(Client client) {
        if (sSimperium != null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Simperium has already been initialized");                
            }
        }
        sSimperium = new Simperium(client);
        return sSimperium;
    }

    public static TrustManager buildPinnedTrustManager(Context context) {
        // Pin SSL to Simperium.com SPKI
        return new PinningTrustManager(SystemKeyStore.getInstance(context),
                                       new String[] { BuildConfig.SIMPERIUM_COM_SPKI }, 0);
    }

    public static SharedPreferences sharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private User mUser;

    final private AuthProvider mAuthProvider;
    final private StorageProvider mPersistentStore;
    final private GhostStorageProvider mGhostStore;
    final private ChannelProvider mChannelProvider;
    final private Executor mExecutor;

    protected OnUserCreatedListener mOnUserCreatedListener;
    protected User.StatusChangeListener mUserListener;

    protected Simperium(Client client){

        mAuthProvider = client.buildAuthProvider();
        mPersistentStore = client.buildStorageProvider();
        mGhostStore = client.buildGhostStorageProvider();
        mChannelProvider = client.buildChannelProvider();
        mExecutor = client.buildExecutor();

        loadUser();

    }

    private void loadUser() {
        mUser = new User(this);
        mAuthProvider.restoreUser(mUser);
        if (mUser.needsAuthorization()) {
            mUser.setStatus(User.Status.NOT_AUTHORIZED);
        }
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
        return bucket(bucketName, schema, mPersistentStore.createStore(bucketName, schema));
    }

    /**
     * Allow alternate storage mechanisms
     */
    public <T extends Syncable> Bucket<T> bucket(String bucketName, BucketSchema<T> schema, StorageProvider.BucketStore<T> storage)
    throws BucketNameInvalid {

        // initialize the bucket
        Bucket<T> bucket = new Bucket<T>(mExecutor, bucketName, schema, mUser, storage, mGhostStore);

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

    public User getUser() {
        return mUser;
    }

    public boolean needsAuthorization(){
        return mUser.needsAuthorization();
    }

    public User createUser(String email, String password, AuthResponseListener listener){
        mUser.setCredentials(email, password);
        AuthResponseListener wrapper = new AuthResponseListenerWrapper(listener){
            @Override
            public void onSuccess(User user){
                super.onSuccess(user);
                notifyOnUserCreatedListener(user);
            }
        };
        mAuthProvider.createUser(AuthUtil.makeAuthRequestBody(mUser), new AuthResponseHandler(mUser, wrapper));
        return mUser;
    }

    public User authorizeUser(String email, String password, AuthResponseListener listener){
        mUser.setCredentials(email, password);
        AuthResponseListener wrapper = new AuthResponseListenerWrapper(listener);
        mAuthProvider.authorizeUser(AuthUtil.makeAuthRequestBody(mUser), new AuthResponseHandler(mUser, wrapper));
        return mUser;
    }

    public void deauthorizeUser(){
        mUser.setAccessToken(null);
        mUser.setEmail(null);
        mAuthProvider.deauthorizeUser(mUser);
        mUser.setStatus(User.Status.NOT_AUTHORIZED);
    }

    public User.StatusChangeListener getUserStatusChangeListener() {
        return mUserListener;
    }

    public void setUserStatusChangeListener(User.StatusChangeListener listener){
        mUserListener = listener;
    }

    @Override
    public void onUserStatusChange(User.Status status){
        if (mUserListener != null) {
            mUserListener.onUserStatusChange(status);
        }
    }

    public void setOnUserCreatedListener(OnUserCreatedListener listener){
        mOnUserCreatedListener = listener;
    }

    protected void notifyOnUserCreatedListener(User user){
        if (mOnUserCreatedListener != null){
            mOnUserCreatedListener.onUserCreated(user);
        }
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

    protected static class ClientImpl implements Client {

        protected final Context mContext;
        protected AsyncHttpClient mHttpClient = AsyncHttpClient.getDefaultInstance();

        final protected SQLiteDatabase mDatabase;
        final protected String mAppId;
        final protected String mAppSecret;
        protected final String mSessionId;
        final private Executor mExecutor;

        ClientImpl(Context context, String appId, String appSecret) {

            mAppId = appId;
            mAppSecret = appSecret;
            mContext = context;

            mDatabase = mContext.openOrCreateDatabase(DEFAULT_DATABASE_NAME, 0, null);

            SharedPreferences preferences = sharedPreferences(mContext);
            String sessionToken = null;

            if (preferences.contains(SESSION_ID_PREFERENCE)) {
                try {
                    sessionToken = preferences.getString(SESSION_ID_PREFERENCE, null);
                } catch (ClassCastException e) {
                    sessionToken = null;
                }
            }

            if (sessionToken == null) {
                sessionToken = Uuid.uuid(6);
                preferences.edit().putString(SESSION_ID_PREFERENCE, sessionToken).commit();
            }

            mSessionId = String.format("%s-%s", Version.LIBRARY_NAME, sessionToken);

            TrustManager[] trustManagers = new TrustManager[] { buildPinnedTrustManager(context) };
            mHttpClient.getSSLSocketMiddleware().setTrustManagers(trustManagers);

            int threads = Runtime.getRuntime().availableProcessors();
            if (threads > 1) {
                threads -= 1;
            }
            Log.d(TAG, "Using " + threads + " cores for executors");
            mExecutor = Executors.newFixedThreadPool(threads);
        }

        @Override
        public AuthProvider buildAuthProvider() {
            return new AsyncAuthClient(mContext, mAppId, mAppSecret, mHttpClient);
        }

        @Override
        public ChannelProvider buildChannelProvider() {
            WebSocketManager.ConnectionProvider provider = new AsyncWebSocketProvider(mAppId, mSessionId, mHttpClient);
            return new WebSocketManager(mExecutor, mAppId, mSessionId, new QueueSerializer(mDatabase), provider);
        }

        @Override
        public PersistentStore buildStorageProvider(){
            return new PersistentStore(mDatabase);
        }

        @Override
        public GhostStorageProvider buildGhostStorageProvider(){
            return new GhostStore(mDatabase);
        }

        @Override
        public Executor buildExecutor(){
            return mExecutor;
        }

    }

}