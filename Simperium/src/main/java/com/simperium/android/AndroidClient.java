package com.simperium.android;

import com.loopj.android.http.*;

import com.simperium.Simperium;
import com.simperium.client.ClientFactory;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.content.SharedPreferences;
import android.util.Log;

import com.simperium.util.Uuid;

/**
 * Refactoring as much of the android specific components of the client
 * and decoupling different parts of the API.
 */
public class AndroidClient implements ClientFactory {

    public static final String DEFAULT_DATABASE_NAME = "simperium-store";

    protected Context mContext;

    public AndroidClient(Context context){
        mContext = context;
    }

    @Override
    public AuthClient buildAuthProvider(String appId, String appSecret){
        AsyncHttpClient httpClient = new AsyncHttpClient();
        httpClient.setUserAgent(Simperium.CLIENT_ID);
        AuthClient client = new AuthClient(appId, appSecret, httpClient);
        client.context = mContext;

        return client;
    }

    @Override
    public WebSocketManager buildChannelProvider(String appId){
        // Simperium Bucket API
        return new WebSocketManager(appId, String.format("%s-%s", Simperium.CLIENT_ID, Uuid.uuid().substring(0,6)), new FileQueueSerializer(mContext));
    }

    @Override
    public PersistentStore buildStorageProvider(){
        return new PersistentStore(mContext.openOrCreateDatabase(DEFAULT_DATABASE_NAME, 0, null));
    }

    @Override
    public GhostStore buildGhostStorageProvider(){
        return new GhostStore(mContext);
    }
    
}