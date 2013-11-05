package com.simperium;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObject;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.User;
import com.simperium.test.MockAuthResponseListener;
import com.simperium.test.MockBucketStore;
import com.simperium.test.MockClient;

import java.util.ArrayList;
import java.util.List;

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

    public void testBuildBucketWithAlternateStorage()
    throws Exception {
        BucketObject.Schema schema = new BucketObject.Schema();
        TestStore store = new TestStore();
        Bucket<BucketObject> bucket = mSimperium.bucket("stuff", schema, store);

        assertTrue(store.prepare);

        BucketObject thing = bucket.newObject();
        thing.setProperty("title", "hola mundo");

        thing.save();
        assertTrue(store.save);

        thing = bucket.get(thing.getSimperiumKey());
        assertTrue(store.get);

        thing.delete();
        assertTrue(store.delete);

    }

    public void testInitialAuthState()
    throws Exception {
        // clear out the saved access token and build a new client for this test
        mClient.authProvider.accessToken = null;
        mSimperium = new Simperium("fake-id", "fake-secret", mClient);
        mSimperium.setUserStatusChangeListener(mAuthListener);

        // we have no access token so we should need authentication
        assertTrue(mSimperium.needsAuthorization());

        // starting state should be NOT_AUTHORIZED since there's no access token
        assertEquals(User.Status.NOT_AUTHORIZED, mSimperium.getUser().getStatus());

        // no auth changes yet
        assertEquals(0, mAuthStatuses.size());
    }

    public void testAuthorized()
    throws Exception {

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
        assertEquals("fake-token", mClient.authProvider.accessToken);
        // make sure callback was called once
        assertEquals(1, mAuthStatuses.size());
    }

    public void testNotAuthorized()
    throws Exception {

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

    public void testDeauthorizeUser()
    throws Exception {

        User user = mSimperium.getUser();
        user.setAccessToken("fake-token");
        user.setStatus(User.Status.AUTHORIZED);

        mSimperium.deauthorizeUser();

        // token should be cleared
        assertTrue(mSimperium.needsAuthorization());
        // token should be clared from auth client
        assertNull(mClient.authProvider.accessToken);
        // last status change should be NOT_AUTHORIZED
        assertEquals(User.Status.NOT_AUTHORIZED, user.getStatus());
    }

    public void testUserCreatedListener()
    throws Exception {
        UserCreatedListener userListener = new UserCreatedListener();
        MockAuthResponseListener responseListener = new MockAuthResponseListener();

        mSimperium.setOnUserCreatedListener(userListener);

        mSimperium.createUser("test@test.com", "12345", responseListener);

        // make sure the handler and listener were actually called
        assertTrue(responseListener.success);
        assertTrue(userListener.userCreated);
    }

    private static class UserCreatedListener implements Simperium.OnUserCreatedListener {

        public boolean userCreated = false;

        @Override
        public void onUserCreated(User user){
            userCreated = true;
        }

    }

    static class TestStore extends MockBucketStore<BucketObject>{

        public boolean prepare = false, save = false, delete = false, get = false;

        @Override
        public void prepare(Bucket<BucketObject> bucket){
            prepare = true;
        }

        @Override
        public void save(BucketObject object, List<Index> indexes){
            save = true;
            super.save(object, indexes);
        }

        @Override
        public void delete(BucketObject object){
            delete = true;
            super.delete(object);
        }

        @Override
        public BucketObject get(String key){
            get = true;
            return super.get(key);
        }

    }

}