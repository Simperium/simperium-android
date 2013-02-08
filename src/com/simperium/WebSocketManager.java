package com.simperium;

import com.simperium.Simperium;
import com.simperium.user.User;
import com.codebutler.android_websockets.*;

import com.simperium.Bucket;
import com.simperium.Channel;
import java.net.URI;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.util.HashMap;
import org.apache.http.message.BasicNameValuePair;


public class WebSocketManager implements WebSocketClient.Listener, Channel.Listener {
    
    private static final String WEBSOCKET_URL = "wss://api.simperium.com/sock/websocket";
    private static final String SOCKETIO_URL = "https://api.simperium.com/";
    private static final String USER_AGENT_HEADER = "User-Agent";
    
    private String appId;
    private String clientId;
    private WebSocketClient socketClient;
    private boolean connected = false;
    private HashMap<Channel,Integer> channels;
    private HashMap<Integer,Channel> channelIndex;
    
    public WebSocketManager(String appId){
        this.appId = appId;
        
        List<BasicNameValuePair> headers = Arrays.asList(
            new BasicNameValuePair(USER_AGENT_HEADER, Simperium.HTTP_USER_AGENT)
        );
        socketClient = new WebSocketClient(URI.create(WEBSOCKET_URL), this, headers);
        channels = new HashMap<Channel,Integer>();
        channelIndex = new HashMap<Integer,Channel>();
    }
    /**
     * Creates a channel for the bucket. Starts the websocket connection if not connected
     *
     */
    public Channel createChannel(Bucket bucket, User user){
        // create a channel
        Channel channel = new Channel(bucket, user);
        channel.addListener(this);
        int channelId = channels.size();
        channels.put(channel, channelId);
        channelIndex.put(channelId, channel);
        // if we're connected tell the channel to fire off it's init message
        // otherwise we need to wait until we're connected
        if (isConnected()) {
            channel.initialize(appId);
        } else {
            connect();
        }
        return channel;
    }
    
    private void connect(){
        socketClient.connect();
    }
    
    public boolean isConnected(){
        return connected;
    }
    
    public boolean getConnected(){
        return isConnected();
    }
    
    protected void setConnected(boolean connected){
        this.connected = connected;
    }
    
    private void initializeChannels(){
        Set<Channel> channelSet = channels.keySet();
        Iterator<Channel> iterator = channelSet.iterator();
        while(iterator.hasNext()){
            Channel channel = iterator.next();
            channel.initialize(appId);
        }
    }
    /**
     *
     * Channel.Listener event listener
     *
     */
    public void onMessage(Channel.MessageEvent event){
        Channel channel = (Channel)event.getSource();
        Integer channelId = channels.get(channel);
        String message = String.format("%d:%s", channelId, event.getMessage());
        Simperium.log(String.format("Sending: %s", message));
        socketClient.send(message);
    }
    /** 
     *
     * WebSocketClient.Listener methods for receiving status events from the socket
     *
     */
    public void onConnect(){
        Simperium.log(String.format("Connect %s", this));
        setConnected(true);
        initializeChannels();
    }
    public void onMessage(String message){
        String[] parts = message.split(":", 2);
        int channelId = Integer.parseInt(parts[0]);
        Channel channel = channelIndex.get(channelId);
        channel.receiveMessage(parts[1]);
    }
    public void onMessage(byte[] data){
        Simperium.log(String.format("From socket (data) %s", new String(data)));
    }
    public void onDisconnect(int code, String reason){
        Simperium.log(String.format("Disconnect %d %s", code, reason));
        setConnected(false);
    }
    public void onError(Exception error){
        org.apache.http.client.HttpResponseException httpError = (org.apache.http.client.HttpResponseException)error;
        Simperium.log(String.format("Error: %d %s", httpError.getStatusCode(), httpError.getMessage()));
        setConnected(false);
    }
    
}