package com.simperium;

import com.simperium.android.WebSocketManager;
import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider;
import com.simperium.models.Note;
import com.simperium.test.MockBucket;
import com.simperium.test.MockConnection;
import com.simperium.test.MockChannelSerializer;
import com.simperium.test.MockExecutor;

import junit.framework.TestCase;

import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.List;


public class WebSocketManagerTest extends TestCase {

    static public final String APP_ID = "mock-app-id";
    static public final String SESSION_ID = "mock-session-id";

    WebSocketManager mSocketManager;
    MockChannelSerializer mChannelSerializer;
    MockConnection mConnection;

    protected void setUp() {

        mChannelSerializer = new MockChannelSerializer();
        // mSocketManager = new WebSocketManager(MockExecutor.immediate(), APP_ID, SESSION_ID, mChannelSerializer, new WebSocketManager.WebSocketFactory() {
        //
        //     @Override
        //     public WebSocketClient buildClient(URI socketURI, WebSocketClient.Listener listener,
        //     List<BasicNameValuePair> headers) {
        //         mSocketClient = new MockWebSocketClient(socketURI, listener, headers);
        //         return mSocketClient;
        //     }
        //
        // });

        mConnection = new MockConnection();

        mSocketManager = new WebSocketManager(MockExecutor.immediate(), APP_ID, SESSION_ID, mChannelSerializer, mConnection.buildProvider());

        // by default assume socket is connected
        mSocketManager.onConnect();

    }

    public void testReceiveLogLevelMessage()
    throws Exception {

        // log level should be set to debug
        mSocketManager.onMessage("log:1");
        assertEquals(ChannelProvider.LOG_DEBUG, mSocketManager.getLogLevel());

        // log level should be set to verbose
        mSocketManager.onMessage("log:2");
        assertEquals(ChannelProvider.LOG_VERBOSE, mSocketManager.getLogLevel());

        // logging should be turned off
        mSocketManager.onMessage("log:0");
        assertEquals(ChannelProvider.LOG_DISABLED, mSocketManager.getLogLevel());

    }

    public void testIgnoreLogWhenLogLevelDisabled()
    throws Exception {

        mSocketManager.log(ChannelProvider.LOG_DEBUG, "This is just a test");
        assertNull(mConnection.lastMessage);

    }

    public void testSendDebugLogWhenLogLevelDebug()
    throws Exception {

        // Turn on debug level
        mSocketManager.onMessage("log:1");

        mSocketManager.log(ChannelProvider.LOG_DEBUG, "debug");
        mSocketManager.log(ChannelProvider.LOG_VERBOSE, "verbose");

        mSocketManager.onMessage("log:0");
        mSocketManager.log(ChannelProvider.LOG_DEBUG, "disabled");

        assertEquals("log:{\"log\":\"debug\"}", mConnection.lastMessage);
    }

    public void testSendVerboseLogWhenLogLevelVerbose()
    throws Exception {

        // Turn on verbose level
        mSocketManager.onMessage("log:2");

        mSocketManager.log(ChannelProvider.LOG_DEBUG, "debug");
        mSocketManager.log(ChannelProvider.LOG_VERBOSE, "verbose");

        mSocketManager.onMessage("log:0");
        mSocketManager.log(ChannelProvider.LOG_VERBOSE, "disabled");

        assertEquals("log:{\"log\":\"verbose\"}", mConnection.lastMessage);
    }

    public void testSendChannelLog()
    throws Exception {

        // build a bucket
        Bucket<Note> bucket = MockBucket.buildBucket(new Note.Schema(), mSocketManager);
        // service turns on verbose logging
        mSocketManager.onMessage("log:2");
        // send log message
        bucket.log(ChannelProvider.LOG_DEBUG, "debug");

        assertEquals("log:{\"log\":\"debug\",\"bucket\":\"notes\"}", mConnection.lastMessage);

    }

}