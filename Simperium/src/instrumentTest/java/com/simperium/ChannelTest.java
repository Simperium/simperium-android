package com.simperium;

import com.simperium.Version;
import com.simperium.client.Bucket;
import com.simperium.client.Channel;
import com.simperium.client.ChannelProvider;
import com.simperium.client.RemoteChange;
import com.simperium.client.User;
import com.simperium.models.Note;
import com.simperium.test.MockBucket;
import com.simperium.test.MockChannelListener;
import com.simperium.test.MockChannelSerializer;
import com.simperium.test.MockGhostStore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import static android.test.MoreAsserts.assertMatchesRegex;
import static com.simperium.TestHelpers.Flag;
import static com.simperium.TestHelpers.waitUntil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class ChannelTest extends BaseSimperiumTest {


    public static String SESSION_ID = "SESSION-ID";
    public static String APP_ID = "APP_ID";

    private MockBucket<Note> mBucket;
    private Channel mChannel;
    private MockChannelSerializer mChannelSerializer;

    protected User.Status mAuthStatus;

    final private MockChannelListener mListener = new MockChannelListener();

    /**
     * Build a Bucket instance that is wired up to a channel using a MockChannelSerializer and a
     * MockChannelListener.
     */
    protected void setUp() throws Exception {
        super.setUp();
        
        mBucket = MockBucket.buildBucket(new Note.Schema(), new ChannelProvider(){

            @Override
            public Bucket.Channel buildChannel(Bucket bucket){
                mChannelSerializer = new MockChannelSerializer();
                mChannel = new Channel(APP_ID, SESSION_ID, bucket, mChannelSerializer, mListener);
                return mChannel;
            }

            @Override
            public void log(int level, CharSequence message) {
                // noop
            }

            @Override
            public int getLogLevel() {
                return 0;
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

    /**
     * When a channel queues up one or more modify operations and then a delete operation, the
     * modify operation should be sent before a delete instead of discarding all queued
     * modifications.
     * 
     * Some objects require a certain state before they can be deleted from a bucket so this ensures
     * the object is at the desired state before the delete operation is sent.
     */
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

    /**
     * Testing the receipt of cv:? from the server when using api 1.1
     */
    public void testReceiveUnknownCV() throws Exception {
        startWithEmptyIndex();
        clearMessages();
        waitForIndex();

        // channel gets a cv:?
        clearMessages();
        mChannel.receiveMessage("cv:?");

        waitForMessage();

        // sends an index request
        assertEquals("i::::50", mListener.lastMessage.toString());

    }

    /**
     * Testing the receipt of c:? from the server when using api 1.1
     */
    public void testReceiveUnknownC() throws Exception {
        startWithEmptyIndex();
        clearMessages();
        waitForIndex();

        // channel gets a cv:?
        clearMessages();
        mChannel.receiveMessage("c:?");

        boolean ignoredMessage = false;
        try {
            waitForMessage();
        } catch (InterruptedException e) {
            ignoredMessage = true;
        }

        assertTrue("Should have ignored c:?", ignoredMessage);

    }

    /**
     * A channel by default is not connected or started until asked.
     */
    public void testChannelInitialState(){
        assertNotNull(mChannel);
        assertFalse(mChannel.isConnected());
        assertFalse(mChannel.isStarted());
    }

    /**
     * Start a bucket to send it's init message and test that it correctly receives an auth message.
     */
    public void testValidAuth(){
        start();

        mChannel.receiveMessage("auth:user@example.com");
        assertEquals(mAuthStatus, User.Status.AUTHORIZED);
    }

    /**
     * A bucket that fails to authorize will receive a deprecated `auth:expired` message and should
     * be ignored.
     */
    public void testIgnoreOldExpiredAuth(){

        start();

        assertNotNull(mListener.lastMessage);
        mChannel.receiveMessage("auth:expired");

        // make sure we ignore the auth:expired
        assertEquals(User.Status.AUTHORIZED, mAuthStatus);
    }

    /**
     * A bucket that fails to authorize will receive and <code>auth:expired</code> followed by an
     * <code>auth:{...}</code> with a JSON payload describing the auth failure.
     * 
     * Receiving an <code>auth:{...}</code> should set the user status to
     * User.Status.NOT_AUTHORIZED
     */
    public void testFailedAuthMessage(){
        start();

        assertNotNull(mListener.lastMessage);
        mChannel.receiveMessage("auth:expired");
        mChannel.receiveMessage("auth:{\"msg\":\"Token invalid\",\"code\":401}");
        assertEquals(User.Status.NOT_AUTHORIZED, mAuthStatus);
    }

    /**
     * A disconnected channel should queue up local modifications.
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

    /**
     * When an object has a change that is waiting to be acknowledged, subsequent changes should be
     * queued.
     */
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

    /**
     * When a channel receives a change from the network for a change that the client set, it should
     * identify the change as acknowledged.
     * 
     * User MockChannelListener's autoAcknowledge to automatically send a valid acknowledged change.
     */
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

    /**
     * When a channel is notified of a connection and started it should correctly identify
     * itself as started and notify its listener.
     */
    public void testStartChannel(){
        // tell the channel that it is connected
        mChannel.onConnect();
        // start the channel
        mChannel.start();

        // it should be started
        assertTrue(mChannel.isStarted());
        assertTrue(mListener.open);
    }

    /**
     * A channel that is started while not connected should automatically start
     * <code>onConnect</code>
     */
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

    /**
     * When started without an index a Channel should send a valid init message with the an initial
     * `i` message.
     */
    public void testInitMessageWithNoChangeVersion(){
        // 
        String initMessage = String.format(Locale.US,
            "init:{\"clientid\":\"%s\",\"cmd\":\"i::::50\",\"token\":\"%s\",\"name\":\"%s\",\"library\":\"%s\",\"api\":\"1.1\",\"app_id\":\"%s\",\"version\":%d}",
            SESSION_ID, mBucket.getUser().getAccessToken(), mBucket.getRemoteName(), "android", APP_ID, 0
        );

        start();

        assertNotNull(mListener.lastMessage);
        assertEquals(initMessage, mListener.lastMessage.toString());
        assertEquals("1.1", mListener.api);
    }

    /**
     * When started with an existing index a Channel should send a valid init message with an
     * initial <code>cv</code> command.
     */
    public void testInitMessageWithChangeVersion(){
        // set a fake change version on the bucket
        String cv = "fake-cv";

        String initMessage = String.format(Locale.US,
            "init:{\"clientid\":\"%s\",\"cmd\":\"cv:%s\",\"token\":\"%s\",\"name\":\"%s\",\"library\":\"%s\",\"api\":\"1.1\",\"app_id\":\"%s\",\"version\":%d}",
            SESSION_ID, cv, mBucket.getUser().getAccessToken(), mBucket.getRemoteName(), "android", APP_ID, 0
        );
        mBucket.setChangeVersion(cv);

        start();

        assertEquals(initMessage, mListener.lastMessage.toString());

    }

    /**
     * Once a channel has an index it should send `c` messages when syncing objects
     */
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

    /**
     * At some point the Simperium service was sending change errors for Changes that did not
     * originate with the client. These responses should be ignored.
     */
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

    public void testReceiveIndexRequest()
    throws Exception {
        String cv = "mock-cv-123";
        Map objects = new HashMap();

        objects.put("mock1.1", "{\"data\":{\"title\":\"1.1\"}}");
        objects.put("mock2.10", "{\"data\":{\"title\":\"2.10\"}}");
        objects.put("mock3.5", "{\"data\":{\"title\":\"3.5\"}}");

        startWithIndex(cv, objects);
        waitForIndex();

        // 0:index:{ current: <cv>, index: { {id: <eid>, v: <version>}, ... }, pending: { { id: <eid>, sv: <version>, ccid: <ccid> }, ... }, extra: { ? } }

        mChannel.receiveMessage("index");

        Channel.MessageEvent message = mListener.lastMessage;

        JSONObject expected = new JSONObject();

        expected.put("index", mListener.indexVersions);
        expected.put("current", cv);

        MockChannelListener.Message parsedMessage = MockChannelListener.parseMessage(message.toString());

        assertEquals("index", parsedMessage.command);

        JSONObject index = new JSONObject(parsedMessage.payload);

        assertEquals(expected.get("current"), index.get("current"));
        assertEquals(expected.getJSONArray("index").length(), index.getJSONArray("index").length());

        assertEquals(mBucket.getName(), index.getJSONObject("extra").getString("bucketName"));
        assertEquals(Version.BUILD, index.getJSONObject("extra").getString("build"));
        assertEquals(Version.NUMBER, index.getJSONObject("extra").getString("version"));
    }

    public void testSendLog()
    throws Exception {
        String message = "message";
        mChannel.log(0, message);

        assertEquals(mListener.logs.get(0), message);
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

    protected void startWithIndex(String cv, Map<String,String> objects)
    throws JSONException, InterruptedException {
        start();
        sendIndex(cv, objects);
        waitForIndex();
    }

    /**
     * Simulates a brand new bucket with and empty index
     */
    protected void sendEmptyIndex(){
        sendMessage("i:{\"index\":[]}");
    }

    protected void sendIndex(String cv, Map<String,String> objects)
    throws JSONException {
        JSONObject index = new JSONObject();
        String period = ".";
        index.put("current", cv);

        JSONArray versions = new JSONArray();
        index.put("index", versions);

        for(Entry<String,String> entry : objects.entrySet()) {
            String key = entry.getKey();
            int dot = key.indexOf(period);
            String id = key.substring(0, dot);
            int version = Integer.parseInt(key.substring(dot+1));
            JSONObject versionData = new JSONObject();
            versionData.put("v", version);
            versionData.put("id", id);

            versions.put(versionData);
        }

        mListener.indexVersions = versions;
        mListener.indexData = objects;
        sendMessage(String.format("i:%s", index));
    }

    protected void sendMessage(String message){
        mChannel.receiveMessage(message);
    }

    protected Channel.MessageEvent waitForMessage() throws InterruptedException {
        return waitForMessage(2000);
    }

    /**
     * Wait until a message received. More than likely clearMessages() should
     * be called before waitForMessage()
     */
    protected Channel.MessageEvent waitForMessage(int waitFor) throws InterruptedException {

        NewMessageFlagger flagger = new NewMessageFlagger();

        waitUntil(flagger, "No message received", waitFor);

        return flagger.message;

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
        }, "Index never received", 5000);
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

    private class NewMessageFlagger implements Flag {

        Channel.MessageEvent message;

        @Override
        public boolean isComplete() {

            message = mListener.lastMessage;

            return mListener.lastMessage != null;

        }

    }

}