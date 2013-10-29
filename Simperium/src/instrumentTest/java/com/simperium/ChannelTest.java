package com.simperium;

import com.simperium.client.Bucket;
import com.simperium.client.Channel;
import com.simperium.client.ChannelProvider;
import com.simperium.client.RemoteChange;
import com.simperium.client.User;
import com.simperium.models.Note;
import com.simperium.test.MockBucket;
import com.simperium.test.MockChannelListener;
import com.simperium.test.MockChannelSerializer;

import java.util.Locale;

import static android.test.MoreAsserts.assertMatchesRegex;
import static com.simperium.TestHelpers.Flag;
import static com.simperium.TestHelpers.waitUntil;

public class ChannelTest extends BaseSimperiumTest {


    public static String SESSION_ID = "SESSION-ID";
    public static String APP_ID = "APP_ID";

    private MockBucket<Note> mBucket;
    private Channel mChannel;
    private MockChannelSerializer mChannelSerializer;

    protected User.Status mAuthStatus;

    final private MockChannelListener mListener = new MockChannelListener();

    protected void setUp() throws Exception {
        super.setUp();
        
        mBucket = MockBucket.buildBucket(new Note.Schema(), new ChannelProvider(){
            @Override
            public Bucket.Channel buildChannel(Bucket bucket){
                mChannelSerializer = new MockChannelSerializer();
                mChannel = new Channel(APP_ID, SESSION_ID, bucket, mChannelSerializer, mListener);
                return mChannel;
            }
        });

        mBucket.getUser().setStatusChangeListener(new User.StatusChangeListener(){
            @Override
            public void onUserStatusChange(User.Status status){
                mAuthStatus = status;
            }
        });

    }

    protected void tearDown() throws Exception {
        mChannel.stop();
        mChannel.reset();
        clearMessages();
        super.tearDown();
    }

    public void testSendFinalModificationBeforeDeleteOperation() throws Exception {

        mListener.autoAcknowledge = true;

        // put channel is started/connected mode with a complete but empty index
        startWithEmptyIndex();

        Note note = mBucket.newObject();
        note.setTitle("Hola mundo!");
        // the will queue up a change
        note.save();

        clearMessages();
        waitForMessage();

        // make the channel disconnect as if it's offline
        mChannel.onDisconnect();

        note.setContent("My hovercraft is full of eels.");
        // this will queue up a change with
        note.save();

        // queue up a {o:-} change
        note.delete();

        // make sure the object creation was acknowledged
        assertEquals(1, mChannelSerializer.ackCount);

        // we should have a queued modification and queued deletion
        assertEquals(2, mChannelSerializer.queue.queued.size());

        assertTrue(mChannelSerializer.queue.queued.get(0).isModifyOperation());
        assertTrue(mChannelSerializer.queue.queued.get(1).isRemoveOperation());

    }


    public void testChannelInitialState(){
        assertNotNull(mChannel);
        assertFalse(mChannel.isConnected());
        assertFalse(mChannel.isStarted());
    }

    public void testValidAuth(){
        start();

        mChannel.receiveMessage("auth:user@example.com");
        assertEquals(mAuthStatus, User.Status.AUTHORIZED);
    }

    public void testIgnoreOldExpiredAuth(){

        start();

        assertNotNull(mListener.lastMessage);
        mChannel.receiveMessage("auth:expired");

        // make sure we ignore the auth:expired
        assertEquals(User.Status.AUTHORIZED, mAuthStatus);
    }

    public void testFailedAuthMessage(){
        start();

        assertNotNull(mListener.lastMessage);
        mChannel.receiveMessage("auth:expired");
        mChannel.receiveMessage("auth:{\"msg\":\"Token invalid\",\"code\":401}");
        assertEquals(User.Status.NOT_AUTHORIZED, mAuthStatus);
    }

    /**
     * Simulates saving items
     */
    public void testOfflineQueueStatus()
    throws Exception {
        // bucket is started and channel is connected
        start();

        // user if off network (airplane mode or similar)
        mChannel.onDisconnect();

        // user saves a new note and kills the app
        Note note = mBucket.newObject();
        note.setTitle("Hola mundo");
        note.setContent("This is a new note!");
        note.save();

        Note note2 = mBucket.newObject();
        note2.setTitle("Second note");
        note2.setContent("This is the second note.");
        note2.save();

        // two different notes are waiting to be sent
        assertEquals(2, mChannelSerializer.queue.queued.size());

    }

    public void testQueuePendingStatus()
    throws Exception {
        startWithEmptyIndex();

        Note note = mBucket.newObject();
        note.setTitle("Hola mundo");
        note.save();

        clearMessages();
        waitForMessage();

        note.setTitle("Second change");
        note.save();

        // first change has been sent and we're waiting to ack
        assertEquals(1, mChannelSerializer.queue.pending.size());
        // second change is waiting for ack before sending
        assertEquals(1, mChannelSerializer.queue.queued.size());
    }

    public void testAcknowledgePending()
    throws Exception {

        startWithEmptyIndex();
        clearMessages();
        mListener.autoAcknowledge = true;

        Note note = mBucket.newObject();
        note.setTitle("Hola mundo");
        note.save();

        waitForMessage();

        assertEquals(1, mChannelSerializer.ackCount);

    }

    public void testStartChannel(){
        // tell the channel that it is connected
        mChannel.onConnect();
        // start the channel
        mChannel.start();

        // it should be started
        assertTrue(mChannel.isStarted());
        assertTrue(mListener.open);
    }

    public void testAutoStartChannel(){
        // try to start channel that isn't connected
        mChannel.start();
        assertFalse(mChannel.isStarted());
        assertFalse(mChannel.isConnected());

        // tell the channel that it is connected and it should automatically start now
        mChannel.onConnect();
        assertTrue(mChannel.isConnected());
        assertTrue(mChannel.isStarted());
        assertTrue(mListener.open);
    }

    public void testInitMessageWithNoChangeVersion(){
        // 
        String initMessage = String.format(Locale.US,
            "init:{\"clientid\":\"%s\",\"cmd\":\"i::::50\",\"token\":\"%s\",\"name\":\"%s\",\"library\":\"%s\",\"api\":1,\"app_id\":\"%s\",\"version\":%d}",
            SESSION_ID, mBucket.getUser().getAccessToken(), mBucket.getRemoteName(), "android", APP_ID, 0
        );

        start();

        assertNotNull(mListener.lastMessage);
        assertEquals(initMessage, mListener.lastMessage.toString());
    }

    public void testInitMessageWithChangeVersion(){
        // set a fake change version on the bucket
        String cv = "fake-cv";

        String initMessage = String.format(Locale.US,
            "init:{\"clientid\":\"%s\",\"cmd\":\"cv:%s\",\"token\":\"%s\",\"name\":\"%s\",\"library\":\"%s\",\"api\":1,\"app_id\":\"%s\",\"version\":%d}",
            SESSION_ID, cv, mBucket.getUser().getAccessToken(), mBucket.getRemoteName(), "android", APP_ID, 0
        );
        mBucket.setChangeVersion(cv);

        start();

        assertEquals(initMessage, mListener.lastMessage.toString());

    }

    public void testSendChange()
    throws Exception {
        start();
        // channel won't send changes until it has an index
        sendEmptyIndex();

        Note note = mBucket.newObject("test-channel-object");
        note.setTitle("Hola mundo");

        clearMessages();
        note.save();

        waitForMessage();
        // message should be a change message "c:{}"
        assertMatchesRegex("^c:\\{.*\\}$", mListener.lastMessage.toString());

    }

    public void testReceiveUnacknowledgedError()
    throws Exception {
        // There are some instances where we are receiving an error from simperium for a ccid we weren't expecting to acknowledge
        RemoteChangeFlagger flagger = new RemoteChangeFlagger();

        mBucket.setRemoteChangeListener(flagger);
        start();
        sendEmptyIndex();

        String errorMessage = "c:[{\"error\": 409, \"ccids\": [\"6c25afe49ac14c3f9b0e9fb7a629118e\"], \"clientid\": \"android-1.0-2dd0aa\", \"id\": \"welcome-android\"}]";
        mChannel.receiveMessage(errorMessage);
        waitForMessage();

        // make sure the RemoteChange error never makes it to the bucket at this point
        assertFalse("Bucket received remote change error", flagger.called);

    }

    /**
     * Get's the channel into a started state
     */
    protected void start(){
        mChannel.onConnect();
        mChannel.start();
        // send auth success message
        mChannel.receiveMessage("auth:user@example.com");
    }

    protected void startWithEmptyIndex()
    throws InterruptedException {
        start();
        sendEmptyIndex();
        waitForIndex();
    }

    /**
     * Simulates a brand new bucket with and empty index
     */
    protected void sendEmptyIndex(){
        mChannel.receiveMessage("i:{\"index\":[]}");
    }

    protected void sendMessage(String message){
        mChannel.receiveMessage(message);
    }

    /**
     * Wait until a message received. More than likely clearMessages() should
     * be called before waitForMessage()
     */
    protected void waitForMessage() throws InterruptedException {
        waitUntil(new Flag(){
            @Override
            public boolean isComplete(){
                return mListener.lastMessage != null;
            }
        }, "No message receieved");
    }

    /**
     * Empties the list of received messages and sets last message to null
     */
    protected void clearMessages(){
        mListener.clearMessages();
    }

    protected void waitForIndex()
    throws InterruptedException {
        waitUntil(new Flag(){
            @Override
            public boolean isComplete(){
                return mChannel.haveCompleteIndex();
            }
        }, "Index never received");
    }

    private static class RemoteChangeFlagger implements MockBucket.RemoteChangeListener {

        public boolean called = false;

        @Override
        public void onApplyRemoteChange(RemoteChange change){
            called = true;
        }

        @Override
        public void onAcknowledgeRemoteChange(RemoteChange change){
            called = true;
        }
    }

}