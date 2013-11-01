package com.simperium;

import com.simperium.client.User;

public class TestHelpers {

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