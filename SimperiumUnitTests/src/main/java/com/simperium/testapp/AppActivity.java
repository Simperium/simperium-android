package com.simperium.testapp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.simperium.Simperium;

import java.util.Properties;

import java.io.InputStream;

public abstract class AppActivity extends Activity
{
    public static String TAG="SimperiumTest";
    private Properties mProperties;
    /** Called when the activity is first created. */

    public String getSimperiumAppId(){
        return getProperty("simperium.appid");
    }

    public String getSimperiumAppSecret(){
        return getProperty("simperium.appsecret");
    }

    public String getSimperiumUserToken(){
        return getProperty("simperium.user.token");
    }

    public String getSimperiumUserEmail(){
        return getProperty("simperium.user.email");
    }

    private void loadProperties() throws java.io.IOException {
        if (mProperties == null) {
            InputStream stream = getResources().getAssets().open("simperium.properties");
            mProperties = new Properties();
            mProperties.load(stream);
        }
    }

    public String getProperty(String property){
        return getProperty(property, "");
    }

    public String getProperty(String property, String fallback){
        return getProperties().getProperty(property, fallback);
    }

    public Properties getProperties(){
        if (mProperties == null) {
            try {
                loadProperties();                
            } catch (java.io.IOException e) {
                Log.e(TAG, "Unable to load simperium.properties", e);
                mProperties = new Properties();
            }
        }
        return mProperties;
    }
}
