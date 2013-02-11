/**
 * When a Bucket is created Simperium creates a Channel to sync changes between
 * a Bucket and simperium.com.
 */
package com.simperium;

import com.simperium.User;
import com.simperium.Bucket;
import com.simperium.Simperium;

import com.simperium.WebSocketManager; 
import java.util.EventObject;
import java.util.EventListener;
import java.util.Enumeration;
import java.util.HashMap;

import org.json.JSONObject;

public class Channel {
    // key names for init command json object
    static final String FIELD_CLIENT_ID   = "clientid";
    static final String FIELD_API_VERSION = "api";
    static final String FIELD_AUTH_TOKEN  = "token";
    static final String FIELD_APP_ID      = "app_id";
    static final String FIELD_BUCKET_NAME = "name";
    static final String FIELD_COMMAND     = "cmd";

    // commands sent over the socket
    static final String COMMAND_INIT      = "init"; // init:{INIT_PROPERTIES}
    static final String COMMAND_AUTH      = "auth"; // received after an init: auth:expired or auth:email@example.com
    static final String COMMAND_INDEX     = "i"; // i:1:MARK:?:LIMIT
    static final String COMMAND_CHANGE    = "c";
    static final String COMMAND_VERSION   = "cv";
    static final String COMMAND_ENTITY    = "e";

    static final String EXPIRED_AUTH      = "expired"; // after unsuccessful init:

    // Parameters for querying bucket
    static final Integer INDEX_PAGE_SIZE  = 500;
    static final Integer INDEX_BATCH_SIZE = 10;
    static final Integer INDEX_QUEUE_SIZE = 5;

    // Constants for parsing command messages
    static final Integer MESSAGE_PARTS = 2;
    static final Integer COMMAND_PART  = 0;
    static final Integer PAYLOAD_PART  = 1;
    static final String  COMMAND_FORMAT = "%s:%s";

    // bucket determines which bucket we are using on this channel
    private Bucket bucket;
    // user's auth token is used to authenticate the channel
    private User user;
    // the object the receives the messages the channel emits
    private Listener listener;
    // track channel status
    private boolean started = false, connected = false, startOnConnect = false;
    private CommandInvoker commands = new CommandInvoker();
    private String appId;
    
    public Channel(String appId, Bucket bucket, User user, Listener listener){
        this.appId = appId;
        this.bucket = bucket;
        this.user = user;
        this.listener = listener;
        // Receive auth: command
        command(COMMAND_AUTH, new Command(){
            public void run(String param){
                if (EXPIRED_AUTH.equals(param.trim())) {
                    // TODO: notify user needs to re-auth
                    return;
                }
                if(hasLastChangeSignature()){
                    startProcessingChanges();
                } else {
                    getLatestVersions();
                }
            }
        });
        // Receive i: command
        command(COMMAND_INDEX, new Command(){
            public void run(String param){
                updateIndex(param);
            }
        });
        // Receive c: command
        command(COMMAND_CHANGE, new Command(){
            public void run(String param){
                handleRemoteChanges(param);
            }
        });
        // Receive e: command
        command(COMMAND_ENTITY, new Command(){
            public void run(String param){
                handleVersionResponse(param);
            }
        });
    }
        
    // TODO: ask bucket if it's initialized yet (if it has a version change?)
    private boolean isInitialized(){
        return false;
    }
    
    // TODO: Ask the bucket for it's last change version
    private boolean hasLastChangeSignature(){
        return false;
    }
    
    private void startProcessingChanges(){
        // TODO: send local changes?
    }
    
    private void getLatestVersions(){
        // TODO: Update bucket's entities with latest data from simperium
    }
    
    private void updateIndex(String indexJson){
        // query i:
        // TODO: Determine check bucket entity versions
    }
    
    private void handleRemoteChanges(String changesJson){
        // TODO: Apply remote changes to current objects
    }
    
    private void handleVersionResponse(String versionJson){
        // TODO: Update entity in bucket?
    }
    
    public Bucket getBucket(){
        return bucket;
    }
    
    public User getUser(){
        return user;
    }
    /**
     * Send Bucket's init message to start syncing changes.
     */
    protected void start(){
        if (started) {
            // we've already started
            return;
        }
        // If socket isn't connected yet we have to wait until connection
        // is up and try starting then
        if (!connected) {
            startOnConnect = true;
            return;
        }
        started = true;
        // If the websocket isn't connected yet we'll automatically start
        // when we're notified that we've connected
        startOnConnect = true;
        // Build the required json object for initializing
        HashMap<String,Object> init = new HashMap<String,Object>(5);
        init.put(FIELD_API_VERSION, 1);
        init.put(FIELD_CLIENT_ID, Simperium.HTTP_USER_AGENT);
        init.put(FIELD_APP_ID, appId);
        init.put(FIELD_AUTH_TOKEN, user.getAccessToken());
        init.put(FIELD_BUCKET_NAME, bucket.getName());
        if (!isInitialized()) {
            init.put(FIELD_COMMAND, new IndexQuery());
        }
        String initParams = new JSONObject(init).toString();
        String message = String.format(COMMAND_FORMAT, COMMAND_INIT, initParams);
        sendMessage(message);
    }
    // websocket 
    protected void onConnect(){
        connected = true;
        if(startOnConnect) start();
    }
    
    protected void onDisconnect(){
        connected = false;
        started = false;
    }
    /**
     * Receive a message from the WebSocketManager which already strips the channel
     * prefix from the message.
     */
    protected void receiveMessage(String message){
        // parse the message and react to it
        String[] parts = message.split(":", MESSAGE_PARTS);
        String command = parts[COMMAND_PART];
        
        run(command, parts[1]);
        
    }
    
    // send without the channel id, the socket manager should know which channel is writing
    private void sendMessage(String message){
        // send a message
        MessageEvent event = new MessageEvent(this, message);
        emit(event);
    }
    
    private void emit(MessageEvent event){
        if (listener != null) {
            listener.onMessage(event);
        } else {
            Simperium.log(String.format("No one listening to channel %s", this));
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
    
    private void command(String name, Command command){
        commands.add(name, command);
    }
    
    private void run(String name, String params){
        commands.run(name, params);
    }
    
    public interface Listener {
        void onMessage(MessageEvent event);
    }
    
    /**
     * Command and CommandInvoker provide a declaritive syntax for handling commands that come in
     * from Channel.onMessage. Takes a message like "auth:user@example.com" and finds the correct
     * command to run and stips the command from the message so the command can take care of
     * processing the params.
     *
     *      channel.command("auth", new Command(){
     *         public void onRun(String params){
     *           // params is now either an email address or "expired"
     *         }
     *      });
     */
    private interface Command {
        void run(String params);
    }
    
    private class CommandInvoker {
        private HashMap<String,Command> commands = new HashMap<String,Command>();
        
        protected CommandInvoker add(String name, Command command){
            commands.put(name, command);
            return this;
        }
        protected void run(String name, String params){
            if (commands.containsKey(name)) {
                Command command = commands.get(name);
                command.run(params);
            } else {
                Simperium.log(String.format("Don't know how to run: %s", name));
            }
        }
    }
    
    static final String CURSOR_FORMAT = "i::%s::%s";
    static final String QUERY_DELIMITER = ":";
    // static final Integer INDEX_MARK = 2;
    // static final Integer INDEX_LIMIT = 5;
    /**
     * IndexQuery provides an interface for managing a query cursor and limit fields.
     * TODO: add a way to build an IndexQuery from an index response
     */
    private class IndexQuery {
        
        private Integer mark = -1;
        private Integer limit = INDEX_PAGE_SIZE;
        
        public IndexQuery(){};
        
        public IndexQuery(Integer limit){
            this.limit = limit;
        }
        
        public IndexQuery(Integer mark, Integer limit){
            this.mark = mark;
            this.limit = limit;
        }
        
        public String toString(){
            String markString = "";
            String limitString = "";
            if (mark > -1) {
                markString = mark.toString();
            }
            if (limit > -1) {
                limitString = limit.toString();
            }
            return String.format(CURSOR_FORMAT, markString, limitString);
        }
        
    }
    
}