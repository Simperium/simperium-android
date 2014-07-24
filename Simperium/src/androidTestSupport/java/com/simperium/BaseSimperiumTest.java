package com.simperium;

import com.simperium.client.Change;

import android.util.Log;

import junit.framework.TestCase;

class BaseSimperiumTest extends TestCase {

    public static final String TAG="SimperiumTest";

    static protected void waitFor(Change change){
        long timeout = 200; // 100 ms timeout
        long start = System.currentTimeMillis();
        Log.d(TAG, "Waiting for change " + change);
        tick();
        while(change.isPending()){
            tick();
            if (System.currentTimeMillis() - start > timeout) {
                throw( new RuntimeException("Change timed out") );
            }
        }
        Log.d(TAG, "Done waiting " + change);
    }
    
    static protected void waitFor(long milliseconds){
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Log.d(TAG, "Interupted");
        }
    }

    static protected void tick(){
        waitFor(1);
    }
    

}
