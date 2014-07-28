package com.simperium;

import com.simperium.client.Change;
import com.simperium.client.User;

public class TestHelpers {

    static protected void waitFor(Change change){
        long timeout = 200; // 100 ms timeout
        long start = System.currentTimeMillis();
        // Log.d(TAG, "Waiting for change " + change);
        tick();
        while(change.isPending()){
            tick();
            if (System.currentTimeMillis() - start > timeout) {
                throw( new RuntimeException("Change timed out") );
            }
        }
        // Log.d(TAG, "Done waiting " + change);
    }
    
    static protected void waitFor(long milliseconds){
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // Log.d(TAG, "Interupted");
        }
    }

    static protected void tick(){
        waitFor(1);
    }

    public static User makeUser(String email, String token){
        User user = new User();
        user.setEmail(email);
        user.setAccessToken(token);
        return user;
    }

    public static User makeUser(String email){
        return makeUser("test@example.com");
    }

    public static User makeUser(){
        return makeUser("test@example.com", "fake-token");
    }

    public static void waitUntil(Flag flag, String message, long timeout)
    throws InterruptedException {
        long start = System.currentTimeMillis();
        while(!flag.isComplete()){
            Thread.sleep(100);
            if (System.currentTimeMillis() - start > timeout) {
                throw(new InterruptedException(message));
            }
        }
    }

    public static void waitUntil(Flag flag, String message)
    throws InterruptedException {
        waitUntil(flag, message, 1000);
    }

    public static void waitUntil(Flag flag)
    throws InterruptedException {
        waitUntil(flag, "Wait timed out");
    }

    public interface Flag {
        abstract boolean isComplete();
    }

}