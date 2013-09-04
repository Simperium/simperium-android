package com.simperium.testapp;

import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import static android.test.MoreAsserts.*;
import static com.simperium.testapp.TestHelpers.*;

import com.simperium.android.LoginActivity;

public class QueueSerializerTest extends ActivityInstrumentationTestCase2<LoginActivity> {

    public QueueSerializerTest() {
        super(LoginActivity.class);
    }

    public void testSanity(){
        assertFalse(":)", true);
    }

}