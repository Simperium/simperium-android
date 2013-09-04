package com.simperium.testapp;

import com.simperium.testapp.models.Note;

import com.simperium.client.Channel;
import com.simperium.client.ChannelProvider;
import com.simperium.client.Bucket;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.client.Change;
import com.simperium.util.Uuid;

import com.simperium.testapp.mock.MockBucket;
import com.simperium.testapp.mock.MockChannelSerializer;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import static android.test.MoreAsserts.*;

public class ChannelTest extends BaseSimperiumTest {


    public static String SESSION_ID = "SESSION-ID";
    public static String APP_ID = "APP_ID";

    private Bucket<Note> mBucket;
    private Channel<Note> mChannel;
    private MockChannelSerializer<Note> mChannelSerializer;

    protected List<Channel.MessageEvent> mMessages = Collections.synchronizedList(new ArrayList<Channel.MessageEvent>());
    protected Channel.MessageEvent mLastMessage;
    protected User.Status mAuthStatus;
    private Boolean mOpen = false;
    protected boolean autoAcknowledge = false;

    final private Channel.OnMessageListener mListener = new Channel.OnMessageListener(){


        @Override
        public void onMessage(Channel.MessageEvent event){
            mMessages.add(event);
            mLastMessage = event;

            if (autoAcknowledge == true && event.message.indexOf("c:") == 0) {
                try {
                    JSONObject changeJSON = new JSONObject(event.message.substring(2));
                    JSONObject ackJSON = new JSONObject();
                    JSONArray ccids = new JSONArray();
                    ccids.put(changeJSON.get("ccid"));
                    ackJSON.put("clientid", mChannel.getSessionId());
                    ackJSON.put("id", changeJSON.getString("id"));
                    ackJSON.put("o", changeJSON.getString("o"));
                    ackJSON.put("v", changeJSON.getJSONObject("v"));
                    ackJSON.put("ccids", ccids);
                    ackJSON.put("cv", Uuid.uuid());
                    int sv = -1;
                    if (changeJSON.has("sv")) {
                        sv = changeJSON.getInt("sv");
                        ackJSON.put("sv", changeJSON.getInt("sv"));
                    }
                    ackJSON.put("ev", sv + 1);

                    JSONArray responseJSON = new JSONArray();
                    responseJSON.put(ackJSON);
                    sendMessage(String.format("c:%s", responseJSON));
                } catch (JSONException e) {
                    throw new RuntimeException(String.format("Couldn't auto-acknowledge %s", event.message), e);
                }
            }
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
        
        mBucket = MockBucket.buildBucket(new Note.Schema(), new ChannelProvider(){
            @Override
            public Bucket.Channel buildChannel(Bucket bucket){
                mChannelSerializer = new MockChannelSerializer<Note>();
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
        clearMessages();
        super.tearDown();
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

        assertNotNull(mLastMessage);
        mChannel.receiveMessage("auth:expired");

        // make sure we ignore the auth:expired
        assertEquals(User.Status.AUTHORIZED, mAuthStatus);
    }

    public void testFailedAuthMessage(){
        start();

        assertNotNull(mLastMessage);
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
        autoAcknowledge = true;

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
        assertTrue(mOpen);
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