package com.simperium.testapp;

import com.simperium.client.test.R;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.simperium.Simperium;
import com.simperium.storage.MemoryStore;
import com.simperium.client.Bucket;

public class TestActivity extends AppActivity
{

    @Override
    public void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

    }

}
