package com.simperium.testapp;

import com.simperium.Simperium;
import com.simperium.client.User;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObject;

import com.simperium.testapp.mock.MockClient;
import com.simperium.testapp.mock.MockAuthResponseHandler;

import java.util.List;
import java.util.ArrayList;

public class SimperiumTest extends BaseSimperiumTest {

    protected Simperium mSimperium;

    protected MockClient mClient;
    protected User.Status mLastStatus;
    protected List<User.Status> mAuthStatuses = new ArrayList<User.Status>();

    protected User.StatusChangeListener mAuthListener = new User.StatusChangeListener(){
        @Override
        public void onUserStatusChange(User.Status status){
            mLastStatus = status;
            mAuthStatuses.add(status);
        }
    };

    protected void setUp() throws Exception {
        super.setUp();

        mClient = new MockClient();
        mSimperium = new Simperium("fake-id", "fake-token", mClient);
        mSimperium.setUserStatusChangeListener(mAuthListener);

    }

    protected void tearDown() throws Exception {
        mLastStatus = null;
        super.tearDown();
    }

    public void testBuildBucket()
    throws Exception {

        Bucket<BucketObject> bucket = mSimperium.bucket("stuff");
        BucketObject object = bucket.newObject();

        object.setProperty("title", "Hola mundo");
        object.save();

        BucketObject other = bucket.get(object.getSimperiumKey());

        assertEquals("Hola mundo", other.getProperty("title"));
        assertEquals(object, other);
    }

    public void testInitialAuthState(){
        // clear out the saved access token and build a new client for this test
        mClient.accessToken = null;
        mSimperium = new Simperium("fake-id", "fake-secret", mClient);
        mSimperium.setUserStatusChangeListener(mAuthListener);

        // we have no access token so we should need authentication
        assertTrue(mSimperium.needsAuthorization());

        // starting state should be NOT_AUTHORIZED since there's no access token
        assertEquals(User.Status.NOT_AUTHORIZED, mSimperium.getUser().getStatus());

        // no auth changes yet
        assertEquals(0, mAuthStatuses.size());
    }

    public void testAuthorized(){

        User user = mSimperium.getUser();
        user.setAccessToken("fake-token");

        // we have an access token so we don't need authentication yet
        assertFalse(mSimperium.needsAuthorization());
        // we haven't received confirmation from the API that our token is valid so it's still UNKNOWN
        assertEquals(User.Status.UNKNOWN, mSimperium.getUser().getStatus());

        // simulate that the user was signed authed successfully
        user.setStatus(User.Status.AUTHORIZED);
        // pretend that a second bucket successfully authed as well
        user.setStatus(User.Status.AUTHORIZED);

        // make sure the authentication listener was called
        assertEquals(User.Status.AUTHORIZED, mLastStatus);
        // make sure the token was saved by the auth client
        assertEquals("fake-token", mClient.accessToken);
        // make sure callback was called once
        assertEquals(1, mAuthStatuses.size());
    }

    public void testNotAuthorized(){

        User user = mSimperium.getUser();
        user.setAccessToken("fake-token");

        // Pretend two buckets failed to auth
        user.setStatus(User.Status.NOT_AUTHORIZED);
        user.setStatus(User.Status.NOT_AUTHORIZED);

        // check the listener's last status is NOT_AUTHENTICATED
        assertEquals(User.Status.NOT_AUTHORIZED, mLastStatus);
        // make sure callback was called once
        assertEquals(1, mAuthStatuses.size());

        // the token shouldn't be cleared unless we explicitly sign out
        assertFalse(mSimperium.needsAuthorization());

    }

    public void testDeauthorizeUser(){

        User user = mSimperium.getUser();
        user.setAccessToken("fake-token");
        user.setStatus(User.Status.AUTHORIZED);

        mSimperium.deauthorizeUser();

        // token should be cleared
        assertTrue(mSimperium.needsAuthorization());
        // token should be clared from auth client
        assertNull(mClient.accessToken);
        // last status change should be NOT_AUTHORIZED
        assertEquals(User.Status.NOT_AUTHORIZED, user.getStatus());
    }

    public void testUserCreatedListener(){
        UserCreatedListener listener = new UserCreatedListener();
        MockAuthResponseHandler handler = new MockAuthResponseHandler();

        mSimperium.setOnUserCreatedListener(listener);

        mSimperium.createUser("test@test.com", "12345", handler);

        // make sure the handler and listener were actually called
        assertTrue(handler.success);
        assertTrue(listener.userCreated);
    }

    private static class UserCreatedListener implements Simperium.OnUserCreatedListener {

        public boolean userCreated = false;

        @Override
        public void onUserCreated(User user){
            userCreated = true;
        }

    }

}