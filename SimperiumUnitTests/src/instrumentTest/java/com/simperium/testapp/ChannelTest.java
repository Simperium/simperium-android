package com.simperium.testapp;

import com.simperium.testapp.models.Note;

import com.simperium.client.Channel;
import com.simperium.client.Bucket;
import com.simperium.client.Syncable;

import com.simperium.testapp.mock.MockBucket;
import com.simperium.testapp.mock.MockChannelSerializer;

import java.util.List;
import java.util.ArrayList;

public class ChannelTest extends SimperiumTest {


    public static String SESSION_ID = "SESSION-ID";
    public static String APP_ID = "APP_ID";

    private Bucket<Note> mBucket;
    private Channel<Note> mChannel;

    protected List<Channel.MessageEvent> mMessages = new ArrayList<Channel.MessageEvent>();
    protected Channel.MessageEvent mLastMessage;
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
                // public Channel(String appId, String sessionId, final Bucket<T> bucket, Serializer serializer, OnMessageListener listener);
                mChannel = new Channel<Note>(APP_ID, SESSION_ID, bucket, new MockChannelSerializer<Note>(), mListener);
                return mChannel;
            }
        });
    }

    public void testChannelInitialState(){
        assertNotNull(mChannel);
        assertTrue(mChannel.isClosed());
        assertFalse(mChannel.isStarted());
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

    protected void start(){
        mChannel.onConnect();
        mChannel.start();
    }

}