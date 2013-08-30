package com.simperium.testapp;

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
    
}