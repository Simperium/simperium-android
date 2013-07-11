package com.simperium.tests.mock;

import com.simperium.client.User;

public class MockUser {

    public static User buildUser(String email, String token){
        User user = new User();
        user.setEmail(email);
        user.setAccessToken(token);
        return user;
    }

    public static User buildUser(String email){
        return buildUser("test@example.com");
    }

    public static User buildUser(){
        return buildUser("test@example.com", "fake-token");
    }

}