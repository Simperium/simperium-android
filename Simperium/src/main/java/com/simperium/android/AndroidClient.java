package com.simperium.android;

import com.loopj.android.http.*;

import com.simperium.Simperium;
import com.simperium.client.ClientFactory;
import com.simperium.util.BasicSyncService;

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
    protected SQLiteDatabase mDatabase;

    public AndroidClient(Context context){
        mContext = context;
        mDatabase = mContext.openOrCreateDatabase(DEFAULT_DATABASE_NAME, 0, null);
    }

    @Override
    public VolleyAuthClient buildAuthProvider(String appId, String appSecret){
        VolleyAuthClient client = new VolleyAuthClient(appId, appSecret, mContext);
        return client;
    }

    @Override
    public WebSocketManager buildChannelProvider(String appId){
        // Simperium Bucket API
        return new WebSocketManager(appId, String.format("%s-%s", Simperium.CLIENT_ID, Uuid.uuid().substring(0,6)), new QueueSerializer(mDatabase));
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
    public LruCacheProvider buildObjectCacheProvider(){
        return new LruCacheProvider();
    }

    @Override
    public BasicSyncService buildSyncService(){
        return new BasicSyncService();
    }
}