package com.simperium.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.WebSocket;

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

    private static final String WEBSOCKET_URL = "https://api.simperium.com/sock/1/%s/websocket";
    private static final String USER_AGENT_HEADER = "User-Agent";

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
    public VolleyAuthClient buildAuthProvider(String appId, String appSecret){
        VolleyAuthClient client = new VolleyAuthClient(appId, appSecret, mContext);
        return client;
    }

    @Override
    public WebSocketManager buildChannelProvider(String appId){
        // Simperium Bucket API
        return new WebSocketManager(mExecutor, appId, mSessionId, new QueueSerializer(mDatabase), new AsyncWebSocketProvider(appId));
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

    class AsyncWebSocketProvider implements WebSocketManager.ConnectionProvider {

        protected final String mAppId;

        AsyncWebSocketProvider(String appId) {
            mAppId = appId;
        }

        @Override
        public void connect(final WebSocketManager.ConnectionListener listener) {

            Uri uri = Uri.parse(String.format(WEBSOCKET_URL, mAppId));

            AsyncHttpRequest request = new AsyncHttpGet(uri);
            request.setHeader(USER_AGENT_HEADER, mSessionId);

            // Protocl is null
            mHttpClient.websocket(request, null, new WebSocketConnectCallback() {

                @Override
                public void onCompleted(Exception ex, final WebSocket webSocket) {
                    if (ex != null) {
                        listener.onError(ex);
                    }

                    final WebSocketManager.Connection connection = new WebSocketManager.Connection() {

                        @Override
                        public void close() {
                            webSocket.close();
                        }

                        @Override
                        public void send(String message) {
                            webSocket.send(message);
                        }

                    };

                    webSocket.setStringCallback(new WebSocket.StringCallback() {

                       @Override
                       public void onStringAvailable(String s) {
                           listener.onMessage(s);
                       }

                    });

                    webSocket.setEndCallback(new CompletedCallback() {

                        @Override
                        public void onCompleted(Exception ex) {
                            listener.onDisconnect(ex);
                        }

                    });

                    listener.onConnect(connection);

                }

            });
        }

    }

}