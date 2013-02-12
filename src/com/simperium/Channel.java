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
import org.json.JSONArray;
import org.json.JSONException;
import android.os.Looper;

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
    static final Integer INDEX_PAGE_SIZE  = 3;
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
                    getUser().setAuthenticationStatus(User.AuthenticationStatus.NOT_AUTHENTICATED);
                    return;
                }
                // This is handled by cmd in init: message 
                // if(hasLastChangeSignature()){
                //     startProcessingChanges();
                // } else {
                //     getLatestVersions();
                // }
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
            
    private boolean hasChangeVersion(){
        return bucket.hasChangeVersion();
    }
    
    private String getChangeVersion(){
        return bucket.getChangeVersion();
    }
    
    private void startProcessingChanges(){
        // TODO: send local changes?
    }
    
    private void getLatestVersions(){
        // TODO: Update bucket's entities with latest data from simperium
    }
    
    private static final String INDEX_CURRENT_VERSION_KEY = "current";
    private static final String INDEX_VERSIONS_KEY = "index";
    private static final String INDEX_MARK_KEY = "mark";
    
    private void updateIndex(String indexJson){
        // query i:
        // FIXME: Threading! http://developer.android.com/reference/java/util/concurrent/package-summary.html
        // TODO: Determine check bucket entity versions
        JSONObject index;
        try {
            index = new JSONObject(indexJson);
        } catch (Exception e) {
            Simperium.log(String.format("Index had invalid json: %s", indexJson));
            return;
        }

        String currentIndex;
        try {
            currentIndex = index.getString(INDEX_CURRENT_VERSION_KEY);
        } catch(JSONException e){
            Simperium.log(String.format("Index did not have current version: %s", indexJson));
            return;
        }
        // TODO: Set the version of this index to remember for future queries
        JSONArray indexVersions;
        try {
            indexVersions = index.getJSONArray(INDEX_VERSIONS_KEY);
        } catch(JSONException e){
            Simperium.log(String.format("Index did not have entities: %s", indexJson));
            return;
        }
        Simperium.log(String.format("Index version: %s", currentIndex));
        
        if (indexVersions.length() > 0) {
            // compare entites with local versions
            // FIXME: collect a full index before comparing versions: IndexProcessor
            getVersionsForKeys(indexVersions);
        }
        // 
        String nextMark = null;
        if (index.has(INDEX_MARK_KEY)) {
            try {
                nextMark = index.getString(INDEX_MARK_KEY);
            } catch (JSONException e) {
                nextMark = null;
            }
        }
        
        if (nextMark != null && nextMark.length() > 0) {
            IndexQuery nextQuery = new IndexQuery(nextMark);
            sendMessage(nextQuery.toString());
        } else {
            // done updating index update the bucket's version key
            bucket.setChangeVersion(currentIndex);
        }
    }
    /**
     * This receives an array of {v:VERSION_INT id:KEY_STRING }. It gets the matching
     * object from the bucket and compares versions. If local version matches, don't need
     * to do anything. Otherwise resolve the difference.
     */
    public static final String INDEX_OBJECT_ID_KEY = "id";
    public static final String INDEX_OBJECT_VERSION_KEY = "v";
    private void getVersionsForKeys(JSONArray keys){
        // FIXME: Threading! http://developer.android.com/reference/java/util/concurrent/package-summary.html
        for (int i=0; i<keys.length(); i++) {
            try {
                JSONObject version = keys.getJSONObject(i);
                String key  = version.getString(INDEX_OBJECT_ID_KEY);
                Integer versionNumber = version.getInt(INDEX_OBJECT_VERSION_KEY);
                if (!bucket.containsKey(key)) {
                    // we need to get the remote entity
                    Simperium.log(String.format("Adding new local entity: %s", version));
                    sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, versionNumber));
                } else if(!bucket.hasKeyVersion(key, versionNumber)) {
                    // we need to get remote changes
                    Integer localVersion = bucket.getKeyVersion(key);
                    Simperium.log(String.format("Get entity changes since: %d", localVersion));
                } else {
                    Simperium.log(String.format("Entity is up to date: %s", version));
                }
                
            } catch (JSONException e) {
                Simperium.log(String.format("Error processing index: %d", i));
                Simperium.log(e.getMessage());
            }
            
        }
    }
    
    private void handleRemoteChanges(String changesJson){
        // TODO: Apply remote changes to current objects
        
    }
    
    private static final String ENTITY_DATA_KEY = "data";
    private void handleVersionResponse(String versionData){
        // versionData will be: key.version\n{"data":ENTITY}
        // we need to parse out the key and version, parse the json payload and
        // retrieve the data
        String[] entityParts = versionData.split("\n");
        String prefix = entityParts[0];
        int lastDot = prefix.lastIndexOf(".");
        if (lastDot == -1) {
            Simperium.log(String.format("Missing version string: %s", prefix));
            return;
        }
        String key = prefix.substring(0, lastDot);
        String version = prefix.substring(lastDot + 1);
        String payload = entityParts[1];
        JSONObject data = null;
        try {
            JSONObject payloadJSON = new JSONObject(payload);
            data = payloadJSON.getJSONObject(ENTITY_DATA_KEY);
        } catch (JSONException e) {
            Simperium.log(e.getMessage());
            return;
        }
        
        Integer remoteVersion = Integer.parseInt(version);
        
        if(bucket.containsKey(key)){
            // check which version we have locally and diff stuff
        } else {
            // construct the bucket object with version data
            // add the bucket object to the bucket
            Entity entity = Entity.fromJSON(key, remoteVersion, data);
            bucket.addEntity(entity);
        }
        
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
        if (!hasChangeVersion()) {
            // the bucket has never gotten an index
            init.put(FIELD_COMMAND, new IndexQuery());
        } else {
            // retive changes since last cv
            init.put(FIELD_COMMAND, String.format("%s:%s", COMMAND_VERSION, getChangeVersion()));
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
    
    static final String CURSOR_FORMAT = "%s::%s::%s";
    static final String QUERY_DELIMITER = ":";
    // static final Integer INDEX_MARK = 2;
    // static final Integer INDEX_LIMIT = 5;
    /**
     * IndexQuery provides an interface for managing a query cursor and limit fields.
     * TODO: add a way to build an IndexQuery from an index response
     */
    private class IndexQuery {
        
        private String mark = "";
        private Integer limit = INDEX_PAGE_SIZE;
        
        public IndexQuery(){};
        
        public IndexQuery(String mark){
            this(mark, INDEX_PAGE_SIZE);
        }
        
        public IndexQuery(Integer limit){
            this.limit = limit;
        }
        
        public IndexQuery(String mark, Integer limit){
            this.mark = mark;
            this.limit = limit;
        }

        public String toString(){
            String limitString = "";
            if (limit > -1) {
                limitString = limit.toString();
            }
            return String.format(CURSOR_FORMAT, COMMAND_INDEX, mark, limitString);
        }
        
    }
    
}