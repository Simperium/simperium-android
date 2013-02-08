package com.simperium;

import com.simperium.user.User;
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
    static final String COMMAND_INIT      = "init";
    static final String COMMAND_AUTH      = "auth";
    static final String COMMAND_EXPIRED   = "expired";
    static final String COMMAND_INDEX     = "i"; // i:1:MARK:?:LIMIT
    static final String COMMAND_CHANGE    = "c";
    static final String COMMAND_VERSION   = "cv";
    static final String COMMAND_ENTITY    = "e";

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
    private boolean started = false;
    private CommandInvoker commands = new CommandInvoker();
    
    public Channel(Bucket bucket, User user, Listener listener){
        this.bucket = bucket;
        this.user = user;
        this.listener = listener;
        
        command(COMMAND_AUTH, new Command(){
            public void run(String param){
                Simperium.log(String.format("AUTH: %s", param));
                setStarted(true);
                if(hasLastChangeSignature()){
                    // start processing changes
                    startProcessingChanges();
                } else {
                    getLatestVersions();
                    // get latest versions
                }
            }
        });
        command(COMMAND_INDEX, new Command(){
            public void run(String param){
                Simperium.log(String.format("INDEX: %s", param));
                updateIndex(param);
            }
        });
        command(COMMAND_CHANGE, new Command(){
            public void run(String param){
                Simperium.log(String.format("CHANGE: %s", param));
                handleRemoteChanges(param);
            }
        });
        command(COMMAND_ENTITY, new Command(){
            public void run(String param){
                Simperium.log(String.format("ENTITY: %s", param));
                handleVersionResponse(param);
            }
        });
        command(COMMAND_EXPIRED, new Command(){
            // expired means the auth token needs to be refreshed
            // we need the user to log in again so the user object
            // needs to be notified
            public void run(String param){
                Simperium.log(String.format("EXPIRED: what now? %s", param));
            }
        });
    }
    
    private void setStarted(boolean started){
        this.started = started;
    }
    
    
    // is the bucket initialized?
    private boolean isInitialized(){
        return false;
    }
        
    private boolean hasLastChangeSignature(){
        return false;
    }
    
    private void startProcessingChanges(){
    }
    
    private void getLatestVersions(){
        
    }
    
    private void updateIndex(String indexJson){
        // query i:
        Simperium.log(String.format("Update your index kids: %s", indexJson));
    }
    
    private void handleRemoteChanges(String changesJson){
        
    }
    
    private void handleVersionResponse(String versionJson){
        
    }
    
    public Bucket getBucket(){
        return bucket;
    }
    
    public User getUser(){
        return user;
    }
    
    protected void start(String appId){
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
    
    protected void stop(){
        started = false;
    }
    
    // the channel id is stripped
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