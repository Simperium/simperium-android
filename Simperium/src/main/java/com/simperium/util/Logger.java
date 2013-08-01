package com.simperium.util;

import android.util.Log;

public class Logger {

    public static final String TAG = "Simperium";

    public static final void log(String msg){
        Log.v(TAG, msg);
    }

    public static final void log(String tag, String msg){
        Log.v(tag, msg);
    }

    public static final void log(String msg, Throwable error){
        Log.e(TAG, msg, error);
    }

    public static final void log(String tag, String msg, Throwable error){
        Log.e(tag, msg, error);
    }

}