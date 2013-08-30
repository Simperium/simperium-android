package com.simperium.client;

public interface AuthProvider {
    void setAuthProvider(String name);

    // attempt to create user
    void createUser(User user, User.AuthResponseHandler handler);

    // attempt to authorize user
    void authorizeUser(User user, User.AuthResponseHandler handler);

    // restore account credentials between sessions
    void restoreUser(User user);

    // clear user token/email
    void deauthorizeUser(User user);

}