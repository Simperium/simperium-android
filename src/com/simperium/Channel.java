/**
 * When a Bucket is created Simperium creates a Channel to sync changes between
 * a Bucket and simperium.com.
 */
package com.simperium.client;

import com.simperium.jsondiff.*;

import java.util.EventObject;
import java.util.EventListener;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Collections;
import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import android.os.Looper;
import android.os.Handler;


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
    
    static final String RESPONSE_UNKNOWN  = "?";

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
	private JSONDiff jsondiff = new JSONDiff();
    
    final private ChangeProcessor changeProcessor;
    private IndexProcessor indexProcessor;
    
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
        
        changeProcessor = new ChangeProcessor(getBucket(), new ChangeProcessorListener(){
            public void onComplete(){
            }
            public void onUpdateEntity(Entity entity){
                Simperium.log(String.format("Updated. Thread: %s", Thread.currentThread().getName()));
                getBucket().updateEntity(entity);
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
        // abort any remote changes since we're getting new data
        changeProcessor.clear();
        // initialize the new query for new index data
        IndexQuery query = new IndexQuery();
        // send the i:::: messages
        sendMessage(query.toString());
    }
    
    private static final String INDEX_CURRENT_VERSION_KEY = "current";
    private static final String INDEX_VERSIONS_KEY = "index";
    private static final String INDEX_MARK_KEY = "mark";
    
    private void updateIndex(String indexJson){
        // if we don't have an index processor, create a new one for the associated cv
        // listen for when the index processor is done so we can start the changeprocessor again
        // if we do have an index processor and the cv's match, add the page of items
        // to the queue.
        // We need to track when we've received a copy of each entity so we can start the change
        // again
        // query i:
        // FIXME: Threading! http://developer.android.com/reference/java/util/concurrent/package-summary.html
        // TODO: Determine check bucket entity versions
        if (indexJson.equals(RESPONSE_UNKNOWN)) {
            // refresh the index
            getLatestVersions();
            return;
        }
        Simperium.log("WTF?");
        JSONObject index;
        try {
            index = new JSONObject(indexJson);
        } catch (Exception e) {
            Simperium.log(String.format("Index had invalid json: %s", indexJson));
            return;
        }
        // if we don't have a processor or we are getting a different cv
        if (indexProcessor == null || !indexProcessor.addIndexPage(index)) {
            // make sure we're not processing changes
            changeProcessor.stop();
            // start a new index
            String currentIndex;
            try {
                currentIndex = index.getString(INDEX_CURRENT_VERSION_KEY);
            } catch(JSONException e){
                Simperium.log("Index did not have current version");
                return;
            }
            
            indexProcessor = new IndexProcessor(getBucket(), currentIndex, new IndexProcessorListener(){
                public void onComplete(String cv){
                    Simperium.log(String.format("Done with index! %s", cv));
                    changeProcessor.start();
                }
            });
            indexProcessor.addIndexPage(index);
        } else {
            Simperium.log("Processing index?");
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
                    Simperium.log(String.format("Requesting entity: %s", version));
                    sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, versionNumber));
                } else if(!bucket.hasKeyVersion(key, versionNumber)) {
                    // we need to get remote changes
                    Integer localVersion = bucket.getKeyVersion(key);
                    Simperium.log(String.format("Get entity changes since: %d", localVersion));
                } else {
                    Simperium.log(String.format("Entity is up to date: %s", version));
                }
                
            } catch (JSONException e) {
                Simperium.log(String.format("Error processing index: %d", i), e);
            }
            
        }
    }
    
    private void handleRemoteChanges(String changesJson){
        JSONArray changes;
        try {
            changes = new JSONArray(changesJson);
        } catch (JSONException e){
            Simperium.log("Failed to parse remote changes JSON", e);
            return;
        }
        // TODO: if we're updating an index we need to queue changes for later
        // Loop through each change? Covert changes to array list
        List<Object> changeList = Entity.convertJSON(changes);
        changeProcessor.addChanges(changeList);
        Simperium.log(String.format("Received %d change(s) queued: %d", changes.length(), changeProcessor.size()));
    }
    
    private static final String ENTITY_DATA_KEY = "data";
    private void handleVersionResponse(String versionData){
        // versionData will be: key.version\n{"data":ENTITY}
        // we need to parse out the key and version, parse the json payload and
        // retrieve the data
        if (indexProcessor == null || !indexProcessor.addEntityData(versionData)) {
          Simperium.log(String.format("Don't know what to do with entity: %s", versionData));
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
    
    private class EntityVersion {
        private String key;
        private Integer version;
        
        public EntityVersion(String key, Integer version){
            this.key = key;
            this.version = version;
        }
        
        public String toString(){
            return String.format("%s.%d", key, version);
        }
    }
    private interface IndexProcessorListener {
        void onComplete(String cv);
    }
    /**
     * When index data is received it should queue up entities in the IndexProcessor.
     * The IndexProcessor then receives the entity data and on a seperate thread asks
     * the StorageProvider to persist the entity data. The storageProvider's operation
     * should not block the websocket thread in any way.
     * 
     * Build up a list of entities and versions we need for the index. Allow the
     * channel to pass in the version data
     */
    private class IndexProcessor implements Runnable {
        
        final private String cv;
        final private Bucket bucket;
        private List<String> index = Collections.synchronizedList(new ArrayList<String>());
        private boolean complete = false;
        private Handler handler;
        final private IndexProcessorListener listener;
        
        public IndexProcessor(Bucket bucket, String cv, IndexProcessorListener listener){
            this.bucket = bucket;
            this.cv = cv;
            this.listener = listener;
        }
        public Boolean addEntityData(String versionData){
            
            String[] entityParts = versionData.split("\n");
            String prefix = entityParts[0];
            int lastDot = prefix.lastIndexOf(".");
            if (lastDot == -1) {
                Simperium.log(String.format("Missing version string: %s", prefix));
                return false;
            }
            String key = prefix.substring(0, lastDot);
            String version = prefix.substring(lastDot + 1);
            String payload = entityParts[1];
            
            if (payload.equals(RESPONSE_UNKNOWN)) {
                Simperium.log(String.format("Entity unkown to simperium: %s.%s", key, version));
                return false;
            }
            
            EntityVersion entityVersion = new EntityVersion(key, Integer.parseInt(version));
            synchronized(index){
                if(!index.remove(entityVersion.toString())){
                    Simperium.log(String.format("Index didn't have %s", entityVersion));
                    return false;
                }
            }
            Simperium.log("We were waiting for this entity let's have the bucket save it");
            
            JSONObject data = null;
            try {
                JSONObject payloadJSON = new JSONObject(payload);
                data = payloadJSON.getJSONObject(ENTITY_DATA_KEY);
            } catch (JSONException e) {
                Simperium.log("Failed to parse entity JSON", e);
                return false;
            }
        
            Integer remoteVersion = Integer.parseInt(version);
        
            if (bucket.containsKey(key)) {
                // check which version we have locally and diff stuff
            } else {
                // construct the bucket object with version data
                // add the bucket object to the bucket
                Entity entity = Entity.fromJSON(key, remoteVersion, data);
                bucket.addEntity(entity);
            }
            
            if (complete && index.size() == 0) {
                listener.onComplete(cv);
            }
            
            return true;
        }
        /**
         * Add the page of data, but only if indexPage cv matches. Detects when it's the
         * last page due to absence of cursor mark
         */
        public Boolean addIndexPage(JSONObject indexPage){
            
            String currentIndex;
            try {
                currentIndex = indexPage.getString(INDEX_CURRENT_VERSION_KEY);
            } catch(JSONException e){
                Simperium.log("Index did not have current version");
                return false;
            }
            
            if (!currentIndex.equals(cv)) {
                return false;
            }
            
            JSONArray indexVersions;
            try {
                indexVersions = indexPage.getJSONArray(INDEX_VERSIONS_KEY);
            } catch(JSONException e){
                Simperium.log(String.format("Index did not have entities: %s", indexPage));
                return true;
            }
            Simperium.log(String.format("received %d entities", indexVersions.length()));
            if (indexVersions.length() > 0) {
                // query for each item that we don't have locally in the bucket
                for (int i=0; i<indexVersions.length(); i++) {
                    try {
                        JSONObject version = indexVersions.getJSONObject(i);
                        String key  = version.getString(INDEX_OBJECT_ID_KEY);
                        Integer versionNumber = version.getInt(INDEX_OBJECT_VERSION_KEY);
                        EntityVersion entityVersion = new EntityVersion(key, versionNumber);
                        if (!bucket.hasKeyVersion(key, versionNumber)) {
                            // we need to get the remote entity
                            synchronized(index){
                                index.add(entityVersion.toString());
                            }
                            Simperium.log(String.format("Requesting entity: %s", version));
                            sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, versionNumber));
                        } else {
                            Simperium.log(String.format("Entity is up to date: %s", version));
                        }
                
                    } catch (JSONException e) {
                        Simperium.log(String.format("Error processing index: %d", i), e);
                    }
            
                }

            }
            // 
            String nextMark = null;
            if (indexPage.has(INDEX_MARK_KEY)) {
                try {
                    nextMark = indexPage.getString(INDEX_MARK_KEY);
                } catch (JSONException e) {
                    nextMark = null;
                }
            }
        
            if (nextMark != null && nextMark.length() > 0) {
                IndexQuery nextQuery = new IndexQuery(nextMark);
                sendMessage(nextQuery.toString());
            } else {
                setComplete();
            }
            
            return true;
        }
        /**
         * Indicates that we have a complete index so when the entities are all populated
         * we know we have all the data
         */
        private void setComplete(){
            complete = true;
            // if we have no pending entity data
            if (index.isEmpty()) {
                // fire off the done listener
                notifyDone();
            }
        }
        
        private void notifyDone(){
            listener.onComplete(cv);
        }
        
        
        public void run(){
        }
        
    }
    
    private interface ChangeProcessorListener {
        /**
         * Change has been applied.
         */
        void onUpdateEntity(Entity entity);
        /**
         * All changes have been processed
         */
        void onComplete();
    }
    /**
     * ChangeProcessor should perform operations on a seperate thread as to not block the websocket
     * ideally it will be a FIFO queue processor so as changes are brought in they can be appended.
     * We also need a way to pause and clear the queue when we download a new index.
     */
    private class ChangeProcessor implements Runnable {
        
        // public static final Integer CAPACITY = 200;
        
        public static final String ID_KEY             = "id";
        public static final String CLIENT_KEY         = "clientid";
        public static final String ENTITY_VERSION_KEY = "ev";
        public static final String SOURCE_VERSION_KEY = "sv";
        public static final String CHANGE_VERSION_KEY = "cv";
        public static final String CHANGE_IDS_KEY     = "ccids";
        public static final String OPERATION_KEY      = JSONDiff.DIFF_OPERATION_KEY;
        public static final String VALUE_KEY          = JSONDiff.DIFF_VALUE_KEY;
        
        public static final String THREAD_NAME        = "change-processor";
        
        private Bucket bucket;
        private JSONDiff diff = new JSONDiff();
        private ChangeProcessorListener listener;
        private Thread thread;
        private ArrayBlockingQueue<Map<String,Object>> queue;
        private Handler handler;
        
        public ChangeProcessor(Bucket bucket, ChangeProcessorListener listener) {
            this.bucket = bucket;
            this.listener = listener;
            this.queue = new ArrayBlockingQueue<Map<String,Object>>(200);
            this.handler = new Handler();
            Simperium.log(String.format("Starting handler on %s thread", Thread.currentThread().getName()));
        }
        
        public void addChanges(List<Object> changes){
            Iterator iterator = changes.iterator();
            while(iterator.hasNext()){
                addChange((Map<String,Object>)iterator.next());
            }
        }
        
        public void addChange(Map<String,Object> change){
            // this blocks until there's capacity in the queue
            queue.offer(change);
        }
        
        public void start(){
            this.thread = new Thread(this, THREAD_NAME);
            this.thread.start();
        }
        
        public void run(){
            // take an item off the queue
            try {
                while(true){
                    // this blocks until there's something on the queue
                    Simperium.log("Process next item");
                    Map<String,Object> change = queue.take();
                    performChange(change);
                    Simperium.log("Done processing");
                }
            } catch(InterruptedException e) {
                Simperium.log("Change processor interrupted");
                return;
            }
        }
        
        public void stop(){
            // interrupt the thread
            if (this.thread != null) {
                this.thread.interrupt();                
            }
        }
        
        public void clear(){
            stop();
            this.queue.clear();
        }
        
        public int size(){
            return this.queue.size();
        }
        
        public int remainingCapacity(){
            return this.queue.remainingCapacity();
        }
        
        private void performChange(Map<String,Object> change){
            
            String entityKey = (String)change.get(ID_KEY);
            Integer sourceVersion = (Integer)change.get(SOURCE_VERSION_KEY);
            Integer entityVersion = (Integer)change.get(ENTITY_VERSION_KEY);
            Entity entity;
            if (null == sourceVersion) {
                // this is a new entity
                entity = new Entity(entityKey);
            } else {
                entity = bucket.get(entityKey);
                if (null == entity) {
                   Simperium.log(String.format("Local entity missing: %s", entityKey));
                   return;
                }
                // we need to check if we have the correct version
                // TODO: handle how to sync if source version doesn't match local entity
                if (!sourceVersion.equals(entity.getVersion())) {
                    Simperium.log(String.format("Local version %d of entity does not match sv: %s %d", entity.getVersion(), entityKey, sourceVersion));
                    return;
                }
            }
            // now we need to apply changes and create a new entity?
            Map<String,Object> patch = (Map<String,Object>)change.get(VALUE_KEY);
            Object operation = change.get(OPERATION_KEY);
            
            Simperium.log(String.format("Apply patch: %s to %s", patch, entity.getDiffableValue()));
            // construct the new entity
            final Entity updated = new Entity(entityKey, entityVersion, jsondiff.apply(entity.getDiffableValue(), patch));
            // notify the listener
            final ChangeProcessorListener changeListener = listener;
            this.handler.post(new Runnable(){
                public void run(){
                    changeListener.onUpdateEntity(updated);
                }
            });
            
        }
        
    }
    
}