package com.simperium.test;

import android.content.Context;

import com.simperium.android.AndroidClient;

public class MockAndroidClient extends AndroidClient {

    public static final String DEFAULT_APP_ID = "app-id";
    public static final String DEFAULT_APP_SECRET = "app-secret";

    public MockAndroidClient(Context context) {
        this(context, DEFAULT_APP_ID, DEFAULT_APP_SECRET);
    }

    protected MockAndroidClient(Context context, String appId, String appSecret) {
        super(context, appId, appSecret);
    }

}