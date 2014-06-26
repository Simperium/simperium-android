package com.simperium.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import com.koushikdutta.async.http.AsyncHttpClient;

import com.simperium.Simperium;
import com.simperium.Version;
import com.simperium.client.ClientFactory;
import com.simperium.util.Uuid;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import android.net.Uri;
import android.util.Log;

/**
 * Refactoring as much of the android specific components of the client
 * and decoupling different parts of the API.
 */
public class AndroidClient implements ClientFactory {

    public static final String TAG = "Simperium.AndroidClient";
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String DEFAULT_DATABASE_NAME = "simperium-store";
    public static final String SESSION_ID_PREFERENCE = "simperium-session-id";

    public static final String WEBSOCKET_URL = "https://api.simperium.com/sock/1/%s/websocket";
    public static final String USER_AGENT_HEADER = "User-Agent";

    protected Context mContext;
    protected SQLiteDatabase mDatabase;
    protected final String mSessionId;

    protected ExecutorService mExecutor;
    protected AsyncHttpClient mHttpClient = AsyncHttpClient.getDefaultInstance();

    public AndroidClient(Context context){
        int threads = Runtime.getRuntime().availableProcessors();
        if (threads > 1) {
            threads -= 1;
        }

        Log.d(TAG, String.format("Using %d cores for executors", threads));
        mExecutor = Executors.newFixedThreadPool(threads);
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
            preferences.edit().putString(SESSION_ID_PREFERENCE, sessionToken).apply();
        }

        mSessionId = String.format("%s-%s", Version.LIBRARY_NAME, sessionToken);
    }

    public static SharedPreferences sharedPreferences(Context context){
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public AsyncAuthClient buildAuthProvider(String appId, String appSecret){
        return new AsyncAuthClient(appId, appSecret, mHttpClient);
    }

    @Override
    public WebSocketManager buildChannelProvider(String appId){
        // Simperium Bucket API
        WebSocketManager.ConnectionProvider provider = new AsyncWebSocketProvider(appId, mSessionId, mHttpClient);
        return new WebSocketManager(mExecutor, appId, mSessionId, new QueueSerializer(mDatabase), provider);
    }

    @Override
    public PersistentStore buildStorageProvider(){
        return new PersistentStore(mDatabase);
    }

    @Override
    public GhostStore buildGhostStorageProvider(){
        return new GhostStore(mDatabase);
    }

    @Override
    public Executor buildExecutor(){
        return mExecutor;
    }

}