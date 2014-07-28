/**
 * Used by Simperium to create a WebSocket connection to Simperium. Manages Channels
 * and listens for channel write events. Notifies channels when the connection is connected
 * or disconnected.
 *
 * WebSocketManager is configured by Simperium and shouldn't need to be access directly
 * by applications.
 * 
 */
package com.simperium.android;

import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

import org.json.JSONException;
import org.json.JSONObject;

import com.simperium.BuildConfig;
import com.simperium.client.Bucket;
import com.simperium.client.Channel;
import com.simperium.client.ChannelProvider;

import android.util.Log;

public class WebSocketManager implements ChannelProvider, Channel.OnMessageListener {

    public enum ConnectionStatus {
        DISCONNECTING, DISCONNECTED, CONNECTING, CONNECTED
    }

    public interface Connection {
        public void close();
        public void send(String message);
    }

    public interface ConnectionListener {
        public void onConnect(Connection connection);
        public void onDisconnect(Exception exception);
        public void onError(Exception exception);
        public void onMessage(String message);
    }

    public interface ConnectionProvider {
        public void connect(ConnectionListener connectionListener);
    }

    public static final String TAG = "Simperium.WebSocket";
    static public final String COMMAND_HEARTBEAT = "h";
    static public final String COMMAND_LOG = "log";
    static public final String LOG_FORMAT = "%s:%s";

    final protected ConnectionProvider mConnectionProvider;
    protected Connection mConnection = new NullConnection();
    protected String mSocketURI;
    private String mAppId, mSessionId;
    private boolean mReconnect = true;
    private HashMap<Channel,Integer> mChannelIndex = new HashMap<Channel,Integer>();
    private HashMap<Integer,Channel> mChannels = new HashMap<Integer,Channel>();

    static final long HEARTBEAT_INTERVAL = 20000; // 20 seconds
    static final long DEFAULT_RECONNECT_INTERVAL = 3000; // 3 seconds

    private Timer mHeartbeatTimer, mReconnectTimer;
    private int mHeartbeatCount = 0, mLogLevel = 0;
    private long mReconnectInterval = DEFAULT_RECONNECT_INTERVAL;

    private ConnectionStatus mConnectionStatus = ConnectionStatus.DISCONNECTED;

    final protected Channel.Serializer mSerializer;
    final protected Executor mExecutor;

    public WebSocketManager(Executor executor, String appId, String sessionId, Channel.Serializer channelSerializer,
        ConnectionProvider connectionProvider) {
        mExecutor = executor;
        mAppId = appId;
        mSessionId = sessionId;
        mSerializer = channelSerializer;
        mConnectionProvider = connectionProvider;
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
            Log.e(TAG, "Could not send log", e);
        }

    }

    protected void log(int level, JSONObject log) {

        // no logging if disabled
        if (mLogLevel == ChannelProvider.LOG_DISABLED) return;

        boolean send = level <= mLogLevel;

        if (BuildConfig.DEBUG) Log.d(TAG, "Log " + level + " => " + log);

        if (send) send(String.format(LOG_FORMAT, COMMAND_LOG, log));
    }

    @Override
    public int getLogLevel() {
        return mLogLevel;
    }

    public void connect() {
        // if we have channels, then connect, otherwise wait for a channel
        cancelReconnect();
        Log.d(TAG, "Asked to connect");
        if (!isConnected() && !isConnecting() && !mChannels.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Connecting to " + mSocketURI);
            }
            setConnectionStatus(ConnectionStatus.CONNECTING);
            mReconnect = true;

            mConnectionProvider.connect(new ConnectionListener() {

                public void onError(Exception exception) {
                    mConnection = new NullConnection();
                    WebSocketManager.this.onError(exception);
                }

                public void onConnect(Connection connection) {
                    mConnection = connection;
                    WebSocketManager.this.onConnect();
                }

                public void onMessage(String message) {
                    WebSocketManager.this.onMessage(message);
                }

                public void onDisconnect(Exception exception) {
                    mConnection = new NullConnection();
                    WebSocketManager.this.onDisconnect(exception);
                }


            });

        }
    }

    protected void send(String message) {
        if (!isConnected()) return;

        synchronized(this) {
            mConnection.send(message);
        }
    }

    public void disconnect() {
        // disconnect the channel
        mReconnect = false;
        cancelReconnect();
        if (isConnected()) {
            setConnectionStatus(ConnectionStatus.DISCONNECTING);
            Log.d(TAG, "Disconnecting");
            // being told to disconnect so don't automatically reconnect
            mConnection.close();
            onDisconnect(null);
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
        for(Channel channel : mChannelIndex.keySet()) {
            channel.onConnect();
        }
    }

    private void notifyChannelsDisconnected() {
        for(Channel channel : mChannelIndex.keySet()) {
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Retrying in " + retryIn);
        }
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

        send(message);

    }

    @Override
    public void onClose(Channel fromChannel) {
        // if we're allready disconnected we can ignore
        if (isDisconnected()) return;

        // check if all channels are disconnected and if so disconnect from the socket
        for (Channel channel : mChannels.values()) {
            if (channel.isStarted()) return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, Thread.currentThread().getName() + " disconnect from socket");
        }
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
            Log.e(TAG, "Unable to send channel log message", e);
        }

    }

    protected void onConnect() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Connected");
        }

        setConnectionStatus(ConnectionStatus.CONNECTED);
        notifyChannelsConnected();
        mHeartbeatCount = 0; // reset heartbeat count
        scheduleHeartbeat();
        cancelReconnect();
        mReconnectInterval = DEFAULT_RECONNECT_INTERVAL;
    }

    protected void onMessage(String message) {

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Received Message: " + message);
        }

        scheduleHeartbeat();

        String[] parts = message.split(":", 2);
        if (parts[0].equals(COMMAND_HEARTBEAT)) {
            mHeartbeatCount = Integer.parseInt(parts[1]);
            return;
        } else if (parts[0].equals(COMMAND_LOG)) {
            mLogLevel = Integer.parseInt(parts[1]);
            return;
        }
        try {
            int channelId = Integer.parseInt(parts[0]);
            Channel channel = mChannels.get(channelId);
            channel.receiveMessage(parts[1]);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Unhandled message " + parts[0], e);
        }
    }

    protected void onDisconnect(Exception ex) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Disconnect " + ex);
        }
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
        notifyChannelsDisconnected();
        cancelHeartbeat();
        if(mReconnect) scheduleReconnect();
    }

    protected void onError(Exception error) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Connection error", error);
        }
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
        if (java.io.IOException.class.isAssignableFrom(error.getClass()) && mReconnect) {
            scheduleReconnect();
        }
    }

    private class NullConnection implements Connection {

        @Override
        public void close() {
            // noop
        }

        @Override
        public void send(String message) {
            // noop
        }

    }

}
