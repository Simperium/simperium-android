/**
 * User is used to determine authentication status for the client. Applications should
 * interact with the User object using the Simperium object's methods:
 *
 *     simperium.createUser( ... ); // register new user
 *     simperium.authorizeUser( ... ); // authorizes an existing user
 *
 * Applications can provide a User.AuthenticationListener to the Simperium object to
 * detect a change in the user's status.
 */
package com.simperium.client;

public class User {
    /**
     * Applications can register a global authentication listener to get notified when a user's
     * authenticated status has changed.
     *
     *     Simperium simperium = new Simperium(appId, appSecret, appContext, new User.AuthenticationListener(){
     *       public void onAuthenticationStatusChange(User.AuthenticationStatus status){
     *          // Prompt user to log in or show they're offline
     *       }
     *     });
     */
    public interface StatusChangeListener {
        void onUserStatusChange(Status authorized);
    }
    /**
     * Determines a user's network status with Simperium:
     *   - AUTHENTICATED: user has an access token and is connected to Simperium
     *   - NOT_AUTHENTICATED: user does not have a valid access token. Create or auth the user
     *   - UKNOWN: User objects start in this state and then transitions to AUTHENTICATED or
     *             NOT_AUTHENTICATED. Also the state given when the Simperium is not reachable.
     */
    public enum Status {
        AUTHORIZED, NOT_AUTHORIZED, UNKNOWN
    }

    private String email;
    private String password;
    private String userId;
    private String accessToken;
    private Status status = Status.UNKNOWN;
    private StatusChangeListener listener;

    public User(){
        this(null, null, null);
    }
    // a user that hasn't been logged in
    public User(StatusChangeListener listener){
        this(null, null, listener);
    }

    public User(String email, StatusChangeListener listener){
        this(email, null, listener);
    }

    public User(String email, String password, StatusChangeListener listener){
        this.email = email;
        this.password = password;
        this.listener = listener;
    }

    public Status getStatus(){
        return status;
    }

    public void setStatusChangeListener(StatusChangeListener authListener){
        listener = authListener;
    }

    public StatusChangeListener getStatusChangeListener(){
        return listener;
    }

    public void setStatus(Status status){
        if (this.status != status) {
            this.status = status;
            if (this.listener != null) {
                listener.onUserStatusChange(this.status);
            }
        }
    }

    // check if we have an access token
    public boolean needsAuthorization(){
        return accessToken == null;
    }

    public boolean hasAccessToken(){
        return accessToken != null;
    }

    public void setCredentials(String email, String password){
        setEmail(email);
        setPassword(password);
    }

    public String getEmail(){
        return email;
    }

    public void setEmail(String email){
        this.email = email;
    }

    public void setPassword(String password){
        this.password = password;
    }

    public String getPassword(){
        return password;
    }

    public String getUserId(){
        return userId;
    }

    protected void setUserId(String userId){
        this.userId = userId;
    }

    public String getAccessToken(){
        return accessToken;
    }

    public void setAccessToken(String token){
        this.accessToken = token;
    }

}
