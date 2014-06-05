/**
 * Used by Simperium to create a WebSocket connection to Simperium. Manages Channels
 * and listens for channel write events. Notifies channels when the connection is connected
 * or disconnected.
 *
 * WebSocketManager is configured by Simperium and shouldn't need to be access directly
 * by applications.
 */
package com.simperium.android;

import com.codebutler.android_websockets.WebSocketClient;
import com.simperium.client.Bucket;
import com.simperium.client.Channel;
import com.simperium.client.ChannelProvider;
import com.simperium.util.Logger;

import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

import org.json.JSONObject;
import org.json.JSONException;

public class WebSocketManager implements ChannelProvider, WebSocketClient.Listener, Channel.OnMessageListener {

    public interface WebSocketFactory {

        public WebSocketClient buildClient(URI socketUri, WebSocketClient.Listener listener,
            List<BasicNameValuePair> headers);
    }

    private static class DefaultSocketFactory implements WebSocketFactory {

        public WebSocketClient buildClient(URI socketUri, WebSocketClient.Listener listener,
            List<BasicNameValuePair> headers) {
            return new WebSocketClient(socketUri, listener, headers);
        }

    }

    public enum ConnectionStatus {
        DISCONNECTING, DISCONNECTED, CONNECTING, CONNECTED
    }

    public static final String TAG = "Simperium.Websocket";
    private static final String WEBSOCKET_URL = "wss://api.simperium.com/sock/1/%s/websocket";
    private static final String USER_AGENT_HEADER = "User-Agent";
    static public final String COMMAND_HEARTBEAT = "h";
    static public final String COMMAND_LOG = "log";
    static public final String LOG_FORMAT = "%s:%s";

    final private AsyncHttpClient mSocketClient;
    private String mAppId, mSessionId;
    private String mClientId;
    private boolean mReconnect = true;
    private HashMap<Channel,Integer> mChannelIndex = new HashMap<Channel,Integer>();
    private HashMap<Integer,Channel> mChannels = new HashMap<Integer,Channel>();
    private Uri mSocketURI;

    static final long HEARTBEAT_INTERVAL = 20000; // 20 seconds
    static final long DEFAULT_RECONNECT_INTERVAL = 3000; // 3 seconds

    private Timer mHeartbeatTimer, mReconnectTimer;
    private int mHeartbeatCount = 0, mLogLevel = 0;
    private long mReconnectInterval = DEFAULT_RECONNECT_INTERVAL;

    private ConnectionStatus mConnectionStatus = ConnectionStatus.DISCONNECTED;

    final protected Channel.Serializer mSerializer;
    final protected Executor mExecutor;

    public WebSocketManager(Executor executor, String appId, String sessionId, Channel.Serializer channelSerializer) {
        this(executor, appId, sessionId, channelSerializer, new DefaultSocketFactory());
    }

    public WebSocketManager(Executor executor, String appId, String sessionId, Channel.Serializer channelSerializer,
        WebSocketFactory socketFactory) {
        mExecutor = executor;
        mAppId = appId;
        mSessionId = sessionId;
        mSerializer = channelSerializer;
        List<BasicNameValuePair> headers = Arrays.asList(
            new BasicNameValuePair(USER_AGENT_HEADER, sessionId)
        );
        socketURI = URI.create(String.format(WEBSOCKET_URL, appId));
        socketClient = socketFactory.buildClient(socketURI, this, headers);
    }

    /**
     * Creates a channel for the bucket. Starts the websocket connection if not connected
     *
     */
    @Override
    public Channel buildChannel(Bucket bucket) {
        // create a channel
        Channel channel = new Channel(mExecutor, mAppId, mSessionId, bucket, mSerializer, this);
        int channelId = mChannels.size();
        mChannelIndex.put(channel, channelId);
        mChannels.put(channelId, channel);
        // If we're not connected then connect, if we don't have a user
        // access token we'll have to hold off until the user does have one
        if (!isConnected() && bucket.getUser().hasAccessToken()) {
            connect();
        } else if (isConnected()) {
            channel.onConnect();
        }
        return channel;
    }

    @Override
    public void log(int level, CharSequence message) {

        try {
            JSONObject log = new JSONObject();
            log.put(COMMAND_LOG, message.toString());
            log(level, log);
        } catch (JSONException e) {
            Logger.log(TAG, "Could not send log", e);
        }

    }

    protected void log(int level, JSONObject log) {

        // no logging if disabled
        if (mLogLevel == ChannelProvider.LOG_DISABLED) return;

        if (level > mLogLevel) return;

        if (!isConnected()) return;
        socketClient.send(String.format(LOG_FORMAT, COMMAND_LOG, log));
    }

    @Override
    public int getLogLevel() {
        return mLogLevel;
    }

    public void connect() {
        // if we have channels, then connect, otherwise wait for a channel
        cancelReconnect();
        if (!isConnected() && !isConnecting() && !mChannels.isEmpty()) {
            Logger.log(TAG, String.format(Locale.US, "Connecting to %s", mSocketURI));
            setConnectionStatus(ConnectionStatus.CONNECTING);
            reconnect = true;
            socketClient.connect();
        }
    }

    public void disconnect() {
        // disconnect the channel
        mReconnect = false;
        cancelReconnect();
        if (isConnected()) {
            setConnectionStatus(ConnectionStatus.DISCONNECTING);
            Logger.log(TAG, "Disconnecting");
            // being told to disconnect so don't automatically reconnect
            socketClient.disconnect();
        }
    }

    public boolean isConnected() {
        return mConnectionStatus == ConnectionStatus.CONNECTED;
    }

    public boolean isConnecting() {
        return mConnectionStatus == ConnectionStatus.CONNECTING;
    }

    public boolean isDisconnected() {
        return mConnectionStatus == ConnectionStatus.DISCONNECTED;
    }

    public boolean isDisconnecting() {
        return mConnectionStatus == ConnectionStatus.DISCONNECTING;
    }

    public boolean getConnected() {
        return isConnected();
    }

    protected void setConnectionStatus(ConnectionStatus status) {
        mConnectionStatus = status;
    }

    private void notifyChannelsConnected() {
        Set<Channel> channelSet = mChannelIndex.keySet();
        Iterator<Channel> iterator = channelSet.iterator();
        while(iterator.hasNext()) {
            Channel channel = iterator.next();
            channel.onConnect();
        }
    }

    private void notifyChannelsDisconnected() {
        Set<Channel> channelSet = mChannelIndex.keySet();
        Iterator<Channel> iterator = channelSet.iterator();
        while(iterator.hasNext()) {
            Channel channel = iterator.next();
            channel.onDisconnect();
        }
    }

    private void cancelHeartbeat() {
        if(mHeartbeatTimer != null) mHeartbeatTimer.cancel();
        mHeartbeatCount = 0;
    }

    private void scheduleHeartbeat() {
        cancelHeartbeat();
        mHeartbeatTimer = new Timer();
        mHeartbeatTimer.schedule(new TimerTask() {
            public void run() {
                sendHearbeat();
            }
        }, HEARTBEAT_INTERVAL);
    }

    synchronized private void sendHearbeat() {
        mHeartbeatCount ++;
        String command = String.format(Locale.US, "%s:%d", COMMAND_HEARTBEAT, mHeartbeatCount);

        send(command);

    }

    private void cancelReconnect() {
        if (mReconnectTimer != null) {
            mReconnectTimer.cancel();
            mReconnectTimer = null;
        }
    }

    private void scheduleReconnect() {
        // check if we're not already trying to reconnect
        if (mReconnectTimer != null) return;
        mReconnectTimer = new Timer();
        // exponential backoff
        long retryIn = nextReconnectInterval();
        mReconnectTimer.schedule(new TimerTask() {
            public void run() {
                connect();
            }
        }, retryIn);
        Logger.log(String.format(Locale.US, "Retrying in %d", retryIn));
    }

    // duplicating javascript reconnect interval calculation
    // doesn't do exponential backoff
    private long nextReconnectInterval() {
        long current = mReconnectInterval;
        if (mReconnectInterval < 4000) {
            mReconnectInterval ++;
        } else {
            mReconnectInterval = 15000;
        }
        return current;
    }

    /**
     *
     * Channel.OnMessageListener event listener
     *
     */
    @Override
    public void onMessage(Channel.MessageEvent event) {
        Channel channel = (Channel)event.getSource();
        Integer channelId = mChannelIndex.get(channel);
        // Prefix the message with the correct channel id
        String message = String.format(Locale.US, "%d:%s", channelId, event.getMessage());

        if(isConnected()){
            socketClient.send(message);
        }
    }

    @Override
    public void onClose(Channel fromChannel) {
        // if we're allready disconnected we can ignore
        if (isDisconnected()) return;

        // check if all channels are disconnected and if so disconnect from the socket
        for (Channel channel : mChannels.values()) {
            if (channel.isStarted()) return;
        }
        Logger.log(TAG, String.format(Locale.US, "%s disconnect from socket", Thread.currentThread().getName()));
        disconnect();
    }

    @Override
    public void onOpen(Channel fromChannel) {
        connect();
    }

    static public final String BUCKET_NAME_KEY = "bucket";
    @Override
    public void onLog(Channel channel, int level, CharSequence message) {
        try {
            JSONObject log = new JSONObject();
            log.put(COMMAND_LOG, message);
            log.put(BUCKET_NAME_KEY, channel.getBucketName());
            log(level, log);
        } catch (JSONException e) {
            Logger.log(TAG, "Unable to send channel log message", e);
        }

    }

    public void onConnect() {
        Logger.log(TAG, String.format("Connected"));
        setConnectionStatus(ConnectionStatus.CONNECTED);
        notifyChannelsConnected();
        mHeartbeatCount = 0; // reset heartbeat count
        scheduleHeartbeat();
        cancelReconnect();
        mReconnectInterval = DEFAULT_RECONNECT_INTERVAL;
    }

    public void onMessage(String message) {
        scheduleHeartbeat();
        int size = message.length();
        String[] parts = message.split(":", 2);;
        if (parts[0].equals(COMMAND_HEARTBEAT)) {
            heartbeatCount = Integer.parseInt(parts[1]);
            return;
        } else if (parts[0].equals(COMMAND_LOG)) {
            logLevel = Integer.parseInt(parts[1]);
            return;
        }
        try {
            int channelId = Integer.parseInt(parts[0]);
            Channel channel = channels.get(channelId);
            channel.receiveMessage(parts[1]);
        } catch (NumberFormatException e) {
            Logger.log(TAG, String.format(Locale.US, "Unhandled message %s", parts[0]));
        }
    }

    public void onMessage(byte[] data) {
    }

    public void onDisconnect(int code, String reason) {
        Logger.log(TAG, String.format(Locale.US, "Disconnect %d %s", code, reason));
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
        notifyChannelsDisconnected();
        cancelHeartbeat();
        if(reconnect) scheduleReconnect();
    }

    public void onError(Exception error) {
        Logger.log(TAG, String.format(Locale.US, "Error: %s", error), error);
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
        if (java.io.IOException.class.isAssignableFrom(error.getClass()) && reconnect) {
            scheduleReconnect();
        }
    }

}
