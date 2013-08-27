package com.simperium.testapp;

import com.simperium.testapp.models.Note;

import com.simperium.client.Channel;
import com.simperium.client.Bucket;
import com.simperium.client.Syncable;
import com.simperium.client.User;

import com.simperium.testapp.mock.MockBucket;
import com.simperium.testapp.mock.MockChannelSerializer;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static android.test.MoreAsserts.*;

public class ChannelTest extends SimperiumTest {


    public static String SESSION_ID = "SESSION-ID";
    public static String APP_ID = "APP_ID";

    private Bucket<Note> mBucket;
    private Channel<Note> mChannel;

    protected List<Channel.MessageEvent> mMessages = Collections.synchronizedList(new ArrayList<Channel.MessageEvent>());
    protected Channel.MessageEvent mLastMessage;
    protected User.AuthenticationStatus mAuthStatus;
    private Boolean mOpen = false;

    final private Channel.OnMessageListener mListener = new Channel.OnMessageListener(){

        @Override
        public void onMessage(Channel.MessageEvent event){
            mMessages.add(event);
            mLastMessage = event;
        }

        @Override
        public void onClose(Channel channel){
            mOpen = false;
        }

        @Override
        public void onOpen(Channel channel){
            mOpen = true;
        }

    };

    protected void setUp() throws Exception {
        super.setUp();
        
        mBucket = MockBucket.buildBucket(new Note.Schema(), new MockBucket.ChannelFactory<Note>(){
            @Override
            public Channel<Note> buildChannel(Bucket<Note> bucket){
                mChannel = new Channel<Note>(APP_ID, SESSION_ID, bucket, new MockChannelSerializer<Note>(), mListener);
                return mChannel;
            }
        });

        mBucket.getUser().setAuthenticationListener(new User.AuthenticationListener(){
            @Override
            public void onAuthenticationStatusChange(User.AuthenticationStatus status){
                mAuthStatus = status;
            }
        });
    }

    protected void tearDown() throws Exception {
        clearMessages();
        super.tearDown();
    }

    public void testChannelInitialState(){
        assertNotNull(mChannel);
        assertTrue(mChannel.isClosed());
        assertFalse(mChannel.isStarted());
    }

    public void testValidAuth(){
        start();

        mChannel.receiveMessage("auth:user@example.com");
        assertEquals(mAuthStatus, User.AuthenticationStatus.AUTHENTICATED);
    }

    public void testExpiredAuth(){

        start();

        // D/Simperium.Websocket(25158): Thread-2286 <= 0:auth:expired
        // D/Simperium.Websocket(25158): Thread-2286 <= 0:auth:{"msg": "Token invalid", "code": 401}
        assertNotNull(mLastMessage);
        mChannel.receiveMessage("auth:expired");
        assertEquals(mAuthStatus, User.AuthenticationStatus.NOT_AUTHENTICATED);
    }

    public void testStartChannel(){
        // tell the channel that it is connected
        mChannel.onConnect();
        // start the channel
        mChannel.start();

        // it should be started
        assertTrue(mChannel.isStarted());
        assertTrue(mOpen);
    }

    public void testAutoStartChannel(){
        // try to start channel that isn't connected
        mChannel.start();
        assertFalse(mChannel.isStarted());

        // tell the channel that it is connected and it should automatically start now
        mChannel.onConnect();
        assertTrue(mChannel.isStarted());
        assertTrue(mOpen);
    }

    public void testInitMessageWithNoChangeVersion(){
        String initMessage = String.format(
            "init:{\"clientid\":\"%s\",\"api\":1,\"app_id\":\"%s\",\"cmd\":\"i::::500\",\"token\":\"%s\",\"name\":\"%s\"}",
            SESSION_ID, APP_ID, mBucket.getUser().getAccessToken(), mBucket.getRemoteName()
        );

        start();

        assertNotNull(mLastMessage);
        assertEquals(initMessage, mLastMessage.toString());
    }

    public void testInitMessageWithChangeVersion(){
        // set a fake change version on the bucket
        String cv = "fake-cv";
        String initMessage = String.format(
            "init:{\"clientid\":\"%s\",\"api\":1,\"app_id\":\"%s\",\"cmd\":\"cv:%s\",\"token\":\"%s\",\"name\":\"%s\"}",
            SESSION_ID, APP_ID, cv, mBucket.getUser().getAccessToken(), mBucket.getRemoteName()
        );
        mBucket.setChangeVersion(cv);

        start();

        assertEquals(initMessage, mLastMessage.toString());

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
        assertMatchesRegex("^c:\\{.*\\}$", mLastMessage.toString());

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

    /**
     * Simulates a brand new bucket with and empty index
     */
    protected void sendEmptyIndex(){
        mChannel.receiveMessage("i:{\"index\":[]}");
    }

    /**
     * Wait until a message received. More than likely clearMessages() should
     * be called before waitForMessage()
     */
    protected void waitForMessage() throws InterruptedException {
        waitUntil(new Flag(){
            @Override
            public boolean isComplete(){
                return mLastMessage != null;
            }
        }, "No message receieved");
    }

    /**
     * Empties the list of received messages and sets mLastMessage to null
     */
    protected void clearMessages(){
        mLastMessage = null;
        mMessages.clear();
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

    protected void waitUntil(Flag flag, String message, long timeout)
    throws InterruptedException {
        long start = System.currentTimeMillis();
        while(!flag.isComplete()){
            Thread.sleep(100);
            if (System.currentTimeMillis() - start > timeout) {
                throw(new RuntimeException(message));
            }
        }
    }

    protected void waitUntil(Flag flag, String message)
    throws InterruptedException {
        waitUntil(flag, message, 1000);
    }

    protected void waitUntil(Flag flag)
    throws InterruptedException {
        waitUntil(flag, "Wait timed out");
    }

    private static abstract class Flag {
        abstract boolean isComplete();
    }

}