package com.simperium.testapp;

import com.simperium.testapp.mock.MockBucket;
import com.simperium.testapp.models.Note;

import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider;
import com.simperium.client.Channel;
import com.simperium.client.Channel.SerializedQueue;
import com.simperium.client.Change;
import com.simperium.client.Syncable;
import com.simperium.client.User;

import java.lang.InterruptedException;
import java.lang.RuntimeException;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class ChannelTest extends SimperiumTest implements MockBucket.ChannelFactory {

    Channel mChannel;
    MockSocket mMockSocket;
    Bucket<Note> mBucket;
    Channel.MessageEvent mLastMessage;

    protected void setUp() throws Exception {
        super.setUp();
        mMockSocket = new MockSocket();
        mBucket = MockBucket.buildBucket(new Note.Schema(), this);
        mBucket.getUser().setAuthenticationStatus(User.AuthenticationStatus.AUTHENTICATED);
        mChannel.onConnect();
        mBucket.start();
    }

    public void testInit(){
        String initMessage = "init:{\"clientid\":\"fake-session\",\"api\":1,\"app_id\":\"fake-app\",\"cmd\":\"i::::500\",\"token\":\"fake-token\",\"name\":\"notes\"}";

        assertEquals(initMessage, mLastMessage.toString());
        assertTrue(mChannel.haveCompleteIndex());
    }

    public void testNewChange() throws Exception {
        mLastMessage = null;
        Note note = mBucket.newObject("fake-note");
        note.setTitle("A note");
        Change change = note.save();
        assertEquals(Change.OPERATION_MODIFY, change.getOperation());
        assertEquals(0, (int) change.getVersion());
        // we need to let the channel's change processor thread operate
        while(!change.isSent()) Thread.sleep(100);

        assertTrue(change.isSent());
        MessageDetails details = MessageDetails.parse(mLastMessage);
        assertEquals(change.getChangeId(), details.json.getString(Change.CHANGE_ID_KEY));
    }

    public void testRevisionRequest() throws Exception {
        String key = "fake-object";
        int version = 10, revisions = version-1;
        mMockSocket.clearHistory();
        RevisionReceiver history = new RevisionReceiver();
        ChannelProvider.RevisionsRequest request = mChannel.getRevisions(key, version, history);
        // send fake entities to the channel
        sendRevisions(key, version);
        
        // make 9 e: requests
        assertEquals(revisions, mMockSocket.history.size());

        // we should wait until the revision request is complete
        while(!request.isComplete()) Thread.sleep(100);

        assertTrue(history.complete);
        assertEquals(revisions, history.revisionCount);
    }

    public <T extends Syncable> ChannelProvider<T> buildChannel(Bucket<T> bucket){
        // public Channel(String appId, String sessionId, final Bucket<T> bucket, Serializer serializer, OnMessageListener listener){
        mChannel = new Channel<T>("fake-app", "fake-session", bucket, new MockSerializer(), mMockSocket );
        return mChannel;
    }

    protected void sendRevisions(String key, int count){
        sendRevisions(key, count, new RevisionBuilder(){
            @Override
            public String buildObjectVersion(String key, int version){
                return String.format("{\"%s\":{\"title\":\"%s.%d\"}}", Channel.ENTITY_DATA_KEY, key, version);
            }
        });
    }

    protected void sendRevisions(String key, int count, RevisionBuilder builder){
        for (int i=1; i<count; i++) {
            mChannel.receiveMessage(String.format("%s:%s.%d\n%s",
                Channel.COMMAND_ENTITY, key, i, builder.buildObjectVersion(key, i)));
        }
    }

    interface RevisionBuilder {
        String buildObjectVersion(String key, int i);
    }

    private class MockSocket implements Channel.OnMessageListener {

        public List<Channel.MessageEvent> history = Collections.synchronizedList(new ArrayList<Channel.MessageEvent>());

        @Override
        public void onMessage(Channel.MessageEvent event){
            // parse out the message
            history.add(0, event);
            String[] parts = event.toString().split(":", 2);
            if (parts[0].equals("init")) {
                // give it an empty bucket index
                mChannel.receiveMessage("i:{\"index\":[]}");
            }
            mLastMessage = event;
        }

        public void clearHistory(){
            history.clear();
        }

    }

    private class MockSerializer implements Channel.Serializer {
        public <T extends Syncable> void save(Bucket<T> bucket, SerializedQueue<T> data){}

        public <T extends Syncable> SerializedQueue<T> restore(Bucket<T> bucket){
            return new SerializedQueue<T>();
        }

        public <T extends Syncable> void reset(Bucket<T> bucket){}

    }

    private static class MessageDetails {

        public JSONObject json;
        public String command;

        MessageDetails(String command, JSONObject details){
            this.command = command;
            this.json = details;
        }

        public static MessageDetails parse(Channel.MessageEvent event) throws JSONException {
            String[] parts = event.toString().split(":", 2);
            return new MessageDetails(parts[0], new JSONObject(parts[1]));
        }

    }

    private class RevisionReceiver implements ChannelProvider.RevisionsRequestCallbacks {

        public boolean complete = false;
        public int revisionCount = 0;

        @Override
        public void onComplete(){
            complete = true;
        }

        @Override
        public void onRevision(String key, int version, Map<String,Object> properties){
            revisionCount ++;
        }

        @Override
        public void onError(Throwable exception){
            throw new RuntimeException(exception);
        }
    }

}