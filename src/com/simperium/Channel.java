package com.simperium;

import com.simperium.user.User;
import com.simperium.Bucket;
import com.simperium.Simperium;

import com.simperium.WebSocketManager; 
import java.util.EventObject;
import java.util.EventListener;
import java.util.Enumeration;
import java.util.HashMap;

import java.util.Vector;

import org.json.JSONObject;

public class Channel {
    
    static final String CLIENT_ID_FIELD   = "clientid";
    static final String API_VERSION_FIELD = "api";
    static final String AUTH_TOKEN_FIELD  = "token";
    static final String APP_ID_FIELD      = "app_id";
    static final String BUCKET_NAME_FIELD = "name";
    
    static final String INIT_COMMAND      = "init";
    
    private Bucket bucket;
    private User user;
    private Vector<Listener> listeners;
    
    public Channel(Bucket bucket, User user){
        // add a listener to the bucket so we can pick up local changes and
        // push the down the socket
        this.bucket = bucket;
        // the user authenticates the channel using the authToken
        this.user = user;
    }
    
    public Bucket getBucket(){
        return bucket;
    }
    
    public User getUser(){
        return user;
    }
    
    protected void initialize(String appId){
        // Build the required json object for initializing
        HashMap<String,Object> init = new HashMap<String,Object>(5);
        init.put(API_VERSION_FIELD, 1);
        init.put(CLIENT_ID_FIELD, Simperium.HTTP_USER_AGENT);
        init.put(APP_ID_FIELD, appId);
        init.put(AUTH_TOKEN_FIELD, user.getAccessToken());
        init.put(BUCKET_NAME_FIELD, bucket.getName());
        
        String initParams = new JSONObject(init).toString();
        String message = String.format("%s:%s", INIT_COMMAND, initParams);
        writeMessage(message);
    }
    
    // the channel id is stripped
    protected void receiveMessage(String message){
        Simperium.log(String.format("Received message: %s", message));
        // parse the message and react to it
    }
    
    // send without the channel id, the socket manager should know which channel is writing
    private void writeMessage(String message){
        // send a message
        MessageEvent event = new MessageEvent(this, message);
        emit(event);
    }
    
    protected void addListener(Listener listener){
        if (listeners == null) {
            listeners = new Vector<Listener>();
        }
        listeners.add(listener);
    }
    
    protected boolean removeListener(Listener listener){
        if (listeners != null && !listeners.isEmpty()) {
            return listeners.remove(listener);
        }
        return false;
    }
    
    private void emit(MessageEvent event){
        if (listeners != null && !listeners.isEmpty()){
            Vector targets;
            synchronized (this){
                targets = (Vector<Listener>) listeners.clone();
            }
            Enumeration<Listener> e = targets.elements();
            while(e.hasMoreElements()){
                Listener listener = e.nextElement();
                listener.onMessage(event);
            }
        }
    }
    
    public class MessageEvent extends EventObject {
        
        public String message;
        
        public MessageEvent(Channel source, String message){
            super(source);
            this.message = message;
        }
        
        public String getMessage(){
            return message;
        }
        
        public String toString(){
            return getMessage();
        }
        
    }
    
    public interface Listener {
        void onMessage(MessageEvent event);
    }
    
}