/**
 * When a Bucket is created Simperium creates a Channel to sync changes between
 * a Bucket and simperium.com.
 *
 * TODO: instead of notifying the bucket about each individual item, there should be 
 * a single event for when there's a "re-index" or after performing all changes in a 
 * change operation.
 *
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Collections;
import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import android.os.Looper;
import android.os.Handler;


public class Channel<T extends Bucket.Syncable> {
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
    private Bucket<T> bucket;
    // user's auth token is used to authenticate the channel
    private User user;
    // the object the receives the messages the channel emits
    private Listener listener;
    // track channel status
    private boolean started = false, connected = false, startOnConnect = false;
    private CommandInvoker commands = new CommandInvoker();
    private String appId;
	private JSONDiff jsondiff = new JSONDiff();
    
    // for sending and receiving changes
    final private ChangeProcessor changeProcessor;
    private IndexProcessor indexProcessor;
    
    public Channel(String appId, Bucket<T> bucket, User user, Listener listener){
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
        
        changeProcessor = new ChangeProcessor<T>(getBucket(), new ChangeProcessorListener<T>(){
            public void onComplete(){
            }
            public void onAddObject(String cv, T object){
                Bucket<T> bucket = getBucket();
                bucket.addObject(cv, object);
            }
            public void onUpdateObject(String cv, T object){
                Bucket<T> bucket = getBucket();
                bucket.updateObject(cv, object);
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
        // abort any remote and local changes since we're getting new data
        changeProcessor.clear();
        // initialize the new query for new index data
        IndexQuery query = new IndexQuery();
        // send the i:::: messages
        sendMessage(query.toString());
    }
    /**
     * Diffs and object's local modifications and queues up the changes
     */
    protected void queueLocalChange(T object){
        // diff the object with it's ghost
        Bucket.Diffable ghost = object.getGhost();
        Map<String,Object> diff = jsondiff.diff(ghost.getDiffableValue(), object.getDiffableValue());
        Simperium.log(String.format("; %s", diff));
        // before we send the change we need a unique id for the ccid
        // public Change(String operation, String key, Integer sourceVersion, Map<String,Object> diff){

        Change change = new Change(Change.OPERATION_MODIFY, object.getSimperiumId(), ghost.getVersion(), (Map<String,Object>)diff.get(JSONDiff.DIFF_VALUE_KEY));
        changeProcessor.addChange(change);
        
        
    }
    
    protected void queueLocalDeletion(T object){
        
    }
    
    protected void queueLocalChange(List<T> objects){
        // queue up a bunch of changes
    }
    
    private static final String INDEX_CURRENT_VERSION_KEY = "current";
    private static final String INDEX_VERSIONS_KEY = "index";
    private static final String INDEX_MARK_KEY = "mark";
    
    private void updateIndex(String indexJson){
        // if we don't have an index processor, create a new one for the associated cv
        // listen for when the index processor is done so we can start the changeprocessor again
        // if we do have an index processor and the cv's match, add the page of items
        // to the queue.
        // We need to track when we've received a copy of each object so we can start the change
        // again
        // query i:
        // FIXME: Threading! http://developer.android.com/reference/java/util/concurrent/package-summary.html
        // TODO: Determine check bucket object versions
        if (indexJson.equals(RESPONSE_UNKNOWN)) {
            // refresh the index
            getLatestVersions();
            return;
        }
        JSONObject index;
        try {
            index = new JSONObject(indexJson);
        } catch (Exception e) {
            Simperium.log(String.format("Index had invalid json: %s", indexJson));
            return;
        }
        // if we don't have a processor or we are getting a different cv
        if (indexProcessor == null || !indexProcessor.addIndexPage(index)) {
            // make sure we're not processing changes and clear pending changes
            changeProcessor.clear();
            // start a new index
            String currentIndex;
            try {
                currentIndex = index.getString(INDEX_CURRENT_VERSION_KEY);
            } catch(JSONException e){
                Simperium.log("Index did not have current version");
                // This should mean that the index is empty and new e.g. no data has
                // never been added, so start the change processor
                changeProcessor.start();
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
    
    private void handleRemoteChanges(String changesJson){
        JSONArray changes;
        if (changesJson.equals(RESPONSE_UNKNOWN)) {
            Simperium.log("CV is out of date");
            changeProcessor.clear();
            getLatestVersions();
            return;
        }
        try {
            changes = new JSONArray(changesJson);
        } catch (JSONException e){
            Simperium.log("Failed to parse remote changes JSON", e);
            return;
        }
        // Loop through each change? Convert changes to array list
        List<Object> changeList = Channel.convertJSON(changes);
        changeProcessor.addChanges(changeList);
        Simperium.log(String.format("Received %d change(s)", changes.length()));
    }
    
    private static final String ENTITY_DATA_KEY = "data";
    private void handleVersionResponse(String versionData){
        // versionData will be: key.version\n{"data":ENTITY}
        // we need to parse out the key and version, parse the json payload and
        // retrieve the data
        if (indexProcessor == null || !indexProcessor.addObjectData(versionData)) {
          Simperium.log(String.format("Don't know what to do with object: %s", versionData));
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
        HashMap<String,Object> init = new HashMap<String,Object>(6);
        init.put(FIELD_API_VERSION, 1);
        init.put(FIELD_CLIENT_ID, Simperium.HTTP_USER_AGENT);
        init.put(FIELD_APP_ID, appId);
        init.put(FIELD_AUTH_TOKEN, user.getAccessToken());
        init.put(FIELD_BUCKET_NAME, bucket.getRemoteName());
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
    
    public static class MessageEvent extends EventObject {
        
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
    
    private class ObjectVersion {
        private String key;
        private Integer version;
        
        public ObjectVersion(String key, Integer version){
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
     * The IndexProcessor then receives the object data and on a seperate thread asks
     * the StorageProvider to persist the object data. The storageProvider's operation
     * should not block the websocket thread in any way.
     * 
     * Build up a list of entities and versions we need for the index. Allow the
     * channel to pass in the version data
     */
    private class IndexProcessor {
        
        public static final String INDEX_OBJECT_ID_KEY = "id";
        public static final String INDEX_OBJECT_VERSION_KEY = "v";
        
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
        public Boolean addObjectData(String versionData){
            
            String[] objectParts = versionData.split("\n");
            String prefix = objectParts[0];
            int lastDot = prefix.lastIndexOf(".");
            if (lastDot == -1) {
                Simperium.log(String.format("Missing version string: %s", prefix));
                return false;
            }
            String key = prefix.substring(0, lastDot);
            String version = prefix.substring(lastDot + 1);
            String payload = objectParts[1];
            
            if (payload.equals(RESPONSE_UNKNOWN)) {
                Simperium.log(String.format("Object unkown to simperium: %s.%s", key, version));
                return false;
            }
            
            ObjectVersion objectVersion = new ObjectVersion(key, Integer.parseInt(version));
            synchronized(index){
                if(!index.remove(objectVersion.toString())){
                    Simperium.log(String.format("Index didn't have %s", objectVersion));
                    return false;
                }
            }
            Simperium.log(String.format("We were waiting for %s.%s", key, version));
            
            JSONObject data = null;
            try {
                JSONObject payloadJSON = new JSONObject(payload);
                data = payloadJSON.getJSONObject(ENTITY_DATA_KEY);
            } catch (JSONException e) {
                Simperium.log("Failed to parse object JSON", e);
                return false;
            }
        
            Integer remoteVersion = Integer.parseInt(version);
            Map<String,Object> properties = Channel.convertJSON(data);
            T object = (T)bucket.buildObject(key, remoteVersion, properties);
        
            if (bucket.containsKey(key)) {
                // check if we have local changes pending?
                bucket.updateObject(object);
            } else {
                // construct the bucket object with version data
                // add the bucket object to the bucket
                bucket.addObject(object);
            }
            
            if (complete && index.size() == 0) {
                notifyDone();
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
                        ObjectVersion objectVersion = new ObjectVersion(key, versionNumber);
                        if (!bucket.hasKeyVersion(key, versionNumber)) {
                            // we need to get the remote object
                            synchronized(index){
                                index.add(objectVersion.toString());
                            }
                            Simperium.log(String.format("Requesting object: %s", version));
                            sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, versionNumber));
                        } else {
                            Simperium.log(String.format("Object is up to date: %s", version));
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
            // if we have no pending object data
            if (index.isEmpty()) {
                // fire off the done listener
                notifyDone();
            }
        }
        
        private void notifyDone(){
            bucket.setChangeVersion(cv);
            listener.onComplete(cv);
        }
                
    }
    
    private interface ChangeProcessorListener<T extends Bucket.Syncable> {
        /**
         * Change has been applied.
         */
        void onUpdateObject(String changeVersion, T object);
        void onAddObject(String changeVersion, T object);
        /**
         * All changes have been processed
         */
        void onComplete();
    }
    private class Change {
        private String operation;
        private String key;
        private Integer version;
        private Map<String,Object> diff;
        private String ccid;
        
        public static final String OPERATION_MODIFY = "M";
        public static final String OPERATION_REMOVE = JSONDiff.OPERATION_REMOVE;
        public static final String ID_KEY           = "id";
        public static final String CHANGE_ID_KEY    = "ccid";
        
        public Change(String operation, String key, Integer sourceVersion, Map<String,Object> diff){
            this.operation = operation;
            this.key = key;
            this.version = sourceVersion;
            this.diff = diff;
            this.ccid = Simperium.uuid();
        }
        /**
         * A JSON representation of this change object
         *
         * {id:key JSONDiff.VALUE_KEY:diff JSONDiff.OPERATION_KEY:operation}
         */
        public String toString(){
            Map<String,Object> map = new HashMap<String,Object>(3);
            map.put(ID_KEY, key);
            map.put(CHANGE_ID_KEY, ccid);
            map.put(JSONDiff.DIFF_OPERATION_KEY, operation);
            map.put(JSONDiff.DIFF_VALUE_KEY, diff);
            JSONObject changeJSON = Channel.serializeJSON(map);
            return changeJSON.toString();
        }
    }
    /**
     * ChangeProcessor should perform operations on a seperate thread as to not block the websocket
     * ideally it will be a FIFO queue processor so as changes are brought in they can be appended.
     * We also need a way to pause and clear the queue when we download a new index.
     */
    private class ChangeProcessor<T extends Bucket.Syncable> implements Runnable {
        
        // public static final Integer CAPACITY = 200;
        
        public static final String ID_KEY             = "id";
        public static final String CLIENT_KEY         = "clientid";
        public static final String ENTITY_VERSION_KEY = "ev";
        public static final String SOURCE_VERSION_KEY = "sv";
        public static final String CHANGE_VERSION_KEY = "cv";
        public static final String CHANGE_IDS_KEY     = "ccids";
        public static final String OPERATION_KEY      = JSONDiff.DIFF_OPERATION_KEY;
        public static final String VALUE_KEY          = JSONDiff.DIFF_VALUE_KEY;
                
        private Bucket<T> bucket;
        private JSONDiff diff = new JSONDiff();
        private ChangeProcessorListener listener;
        private Thread thread;
        // private ArrayBlockingQueue<Map<String,Object>> queue;
        private List<Map<String,Object>> remoteQueue;
        private List<Change> localQueue;
        private Handler handler;
        
        public ChangeProcessor(Bucket<T> bucket, ChangeProcessorListener<T> listener) {
            this.bucket = bucket;
            this.listener = listener;
            this.remoteQueue = Collections.synchronizedList(new ArrayList<Map<String,Object>>());
            this.localQueue = Collections.synchronizedList(new ArrayList<Change>());
            this.handler = new Handler();
        }
        
        public void addChanges(List<Object> changes){
            synchronized(remoteQueue) {
                Iterator iterator = changes.iterator();
                while(iterator.hasNext()){
                    remoteQueue.add((Map<String,Object>)iterator.next());
                }
            }
            start();
        }
        
        public void addChange(Map<String,Object> change){
            synchronized(remoteQueue){
                remoteQueue.add(change);
            }
            start();
        }
        /**
         * Local change to be queued
         */
        public void addChange(Change change){
            synchronized(localQueue){
                localQueue.add(change);
            }
            start();
        }
        
        public void start(){
            if (thread == null || thread.getState() == Thread.State.TERMINATED) {
                Simperium.log("Starting up the change processor");
                thread = new Thread(this);
                thread.start();
            }
        }
        
        public void run(){
            // take an item off the queue
            Map<String,Object> remoteChange;
            do {
                remoteChange = null;
                synchronized(remoteQueue){
                    if (remoteQueue.size() > 0) {
                        remoteChange = remoteQueue.remove(0);
                    }
                }
                if (remoteChange != null){
                    performChange(remoteChange);
                }
            } while (remoteChange != null && !Thread.interrupted());
            Change localChange;
            do {
                localChange = null;
                synchronized(localQueue){
                    if (localQueue.size() > 0) {
                        localChange = localQueue.remove(0);
                    }
                }
                if(localChange != null){
                    sendChange(localChange);
                }
            } while (localChange != null && !Thread.interrupted());
            Simperium.log(String.format("Done processing thread with %d remote and %d local changes", remoteQueue.size(), localQueue.size()));
        }
        
        public void stop(){
            // interrupt the thread
            if (this.thread != null) {
                this.thread.interrupt();                
            }
        }
        
        public void clear(){
            stop();
            this.remoteQueue.clear();
            this.localQueue.clear();
        }

        private void sendChange(Change change){
            // send the change down the socket!
            Simperium.log(String.format("Send local change: %s", change));
            sendMessage(String.format("c:%s", change.toString()));
        }
        
        private void performChange(Map<String,Object> change){
            
            String objectKey = (String)change.get(ID_KEY);
            Integer sourceVersion = (Integer)change.get(SOURCE_VERSION_KEY);
            Integer objectVersion = (Integer)change.get(ENTITY_VERSION_KEY);
            T object;
            if (null == sourceVersion) {
                // this is a new object
                object = bucket.buildObject(objectKey);
            } else {
                object = bucket.get(objectKey);
                if (null == object) {
                   Simperium.log(String.format("Local object missing: %s", objectKey));
                   return;
                }
                // we need to check if we have the correct version
                // TODO: handle how to sync if source version doesn't match local object
                if (!sourceVersion.equals(object.getVersion())) {
                    Simperium.log(String.format("Local version %d of object does not match sv: %s %d", object.getVersion(), objectKey, sourceVersion));
                    return;
                }
            }
            // now we need to apply changes and create a new object?
            Map<String,Object> patch = (Map<String,Object>)change.get(VALUE_KEY);
            Object operation = change.get(OPERATION_KEY);
            final String changeVersion = (String)change.get(CHANGE_VERSION_KEY);
            
            // construct the new object
            Simperium.log(String.format("Try to apply %s to %s", patch, object.getDiffableValue()));
            final T updated = bucket.buildObject(objectKey, objectVersion, jsondiff.apply(object.getDiffableValue(), patch));
            // notify the listener
            final ChangeProcessorListener changeListener = listener;
            final boolean isNew = object.isNew();
            this.handler.post(new Runnable(){
                public void run(){
                    if(isNew){
                        changeListener.onAddObject(changeVersion, updated);
                    } else {
                        changeListener.onUpdateObject(changeVersion, updated);
                    }
                }
            });
            
        }
        
    }
    
    public static Map<String,java.lang.Object> convertJSON(JSONObject json){
        Map<String,java.lang.Object> map = new HashMap<String,java.lang.Object>(json.length());
        Iterator keys = json.keys();
        while(keys.hasNext()){
            String key = (String)keys.next();
            try {
                java.lang.Object val = json.get(key);
                // log(String.format("Hello! %s", json.get(key).getClass().getName()));
                if (val.getClass().equals(JSONObject.class)) {
                    map.put(key, convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    map.put(key, convertJSON((JSONArray) val));
                } else {
                    map.put(key, val);
                }
            } catch (JSONException e) {
                Simperium.log(String.format("Error: %s", e.getMessage()), e);
            }
        }
        return map;
    }
    
    public static List<java.lang.Object> convertJSON(JSONArray json){
        List<java.lang.Object> list = new ArrayList<java.lang.Object>(json.length());
        for (int i=0; i<json.length(); i++) {
            try {
                java.lang.Object val = json.get(i);
                if (val.getClass().equals(JSONObject.class)) {
                    list.add(convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    list.add(convertJSON((JSONArray) val));
                } else {
                    list.add(val);
                }
            } catch (JSONException e) {
                Simperium.log(String.format("Error: %s", e.getMessage()), e);
            }

        }
        return list;
    }
        
    public static JSONObject serializeJSON(Map<String,Object>map){
        JSONObject json = new JSONObject();
        Iterator<String> keys = map.keySet().iterator();
        while(keys.hasNext()){
            String key = keys.next();
            Object val = map.get(key);
            try {
                if (val instanceof Map) {
                    json.put(key, serializeJSON((Map<String,Object>) val));
                } else if(val instanceof List){
                    json.put(key, serializeJSON((List<Object>) val));
                } else {
                    json.put(key, val);
                }
            } catch(JSONException e){
               Simperium.log(String.format("Failed to serialize %s", val));
            }
        }
        return json;
    }
    
    public static JSONArray serializeJSON(List<Object>list){
        JSONArray json = new JSONArray();
        Iterator<Object> vals = list.iterator();
        while(vals.hasNext()){
            Object val = vals.next();
            if (val instanceof Map) {
                json.put(serializeJSON((Map<String,Object>) val));
            } else if(val instanceof List) {
                json.put(serializeJSON((List<Object>) val));
            } else {
                json.put(val);
            }
        }
        return json;
    }
    
}