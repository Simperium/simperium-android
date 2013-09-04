/**
 * When a Bucket is created Simperium creates a Channel to sync changes between
 * a Bucket and simperium.com.
 *
 * A Channel is provided with a Simperium App ID, a Bucket to operate on, a User
 * who owns the bucket and a Channel.Listener that receives messages from the
 * Channel. 
 * 
 * To get messages into a Channel, Channel.receiveMessage receives a Simperium
 * websocket API message stripped of the channel ID prefix.
 * 
 * TODO: instead of notifying the bucket about each individual item, there should be
 * a single event for when there's a "re-index" or after performing all changes in a
 * change operation.
 *
 */
package com.simperium.client;

import com.simperium.Simperium;
import com.simperium.util.JSONDiff;
import com.simperium.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Channel<T extends Syncable> implements Bucket.Channel<T> {

    public interface OnMessageListener {
        void onMessage(MessageEvent event);
        void onClose(Channel channel);
        void onOpen(Channel channel);
    }

    public static final String TAG="Simperium.Channel";
    // key names for init command json object
    static final String FIELD_CLIENT_ID   = "clientid";
    static final String FIELD_API_VERSION = "api";
    static final String FIELD_AUTH_TOKEN  = "token";
    static final String FIELD_APP_ID      = "app_id";
    static final String FIELD_BUCKET_NAME = "name";
    static final String FIELD_COMMAND     = "cmd";

    // commands sent over the socket
    public static final String COMMAND_INIT      = "init"; // init:{INIT_PROPERTIES}
    public static final String COMMAND_AUTH      = "auth"; // received after an init: auth:expired or auth:email@example.com
    public static final String COMMAND_INDEX     = "i"; // i:1:MARK:?:LIMIT
    public static final String COMMAND_CHANGE    = "c";
    public static final String COMMAND_VERSION   = "cv";
    public static final String COMMAND_ENTITY    = "e";

    static final String RESPONSE_UNKNOWN  = "?";

    static final String EXPIRED_AUTH      = "expired"; // after unsuccessful init:
    static final String EXPIRED_AUTH_INDICATOR = "{";
    static final String EXPIRED_AUTH_REASON_KEY = "msg";
    static final String EXPIRED_AUTH_CODE_KEY = "code";
    static final int EXPIRED_AUTH_INVALID_TOKEN_CODE = 401;


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
    // the object the receives the messages the channel emits
    private OnMessageListener listener;
    // track channel status
    protected boolean started = false, connected = false, startOnConnect = false, stopOnIdle = false;
    private boolean haveIndex = false;
    private CommandInvoker commands = new CommandInvoker();
    private String appId, sessionId;
    private Serializer serializer;

    // for sending and receiving changes
    final private ChangeProcessor changeProcessor;
    private IndexProcessor indexProcessor;
    
    public interface Serializer<T extends Syncable> {
        // public <T extends Syncable> void save(Bucket<T> bucket, SerializedQueue<T> data);
        public SerializedQueue<T> restore(Bucket<T> bucket);
        public void reset(Bucket<T> bucket);
        public void onQueueChange(Change change);
        public void onDequeueChange(Change change);
        public void onSendChange(Change change);
        public void onAcknowledgeChange(Change change);
    }

    public static class SerializedQueue<T extends Syncable> {
        final public Map<String,Change> pending;
        final public List<Change> queued;

        public SerializedQueue(){
            this(new HashMap<String, Change>(), new ArrayList<Change>());
        }

        public SerializedQueue(Map<String,Change> pendingChanges, List<Change> queuedChanges){
            this.pending = pendingChanges;
            this.queued = queuedChanges;
        }
    }

    public Channel(String appId, String sessionId, final Bucket<T> bucket, Serializer serializer, OnMessageListener listener){
        this.serializer = serializer;
        this.appId = appId;
        this.sessionId = sessionId;
        this.bucket = bucket;
        this.listener = listener;
        // Receive auth: command
        command(COMMAND_AUTH, new Command(){
            public void run(String param){
                User user = getUser();
                // ignore auth:expired, implement new auth:{JSON} for failures
                if(EXPIRED_AUTH.equals(param.trim())) return;
                // if it starts with { let's see if it's error JSON
                if (param.indexOf(EXPIRED_AUTH_INDICATOR) == 0) {
                    try {
                        JSONObject authResponse = new JSONObject(param);
                        int code = authResponse.getInt(EXPIRED_AUTH_CODE_KEY);
                        if (code == EXPIRED_AUTH_INVALID_TOKEN_CODE) {
                            user.setStatus(User.Status.NOT_AUTHORIZED);
                            stop();
                            return;
                        } else {
                            // TODO retry auth?
                            Logger.log(TAG, String.format("Unable to auth: %d", code));
                            return;
                        }
                    } catch (JSONException e) {
                        Logger.log(TAG, String.format("Unable to parse auth JSON, assume was email %s", param));
                    }
                }
                user.setEmail(param);
                user.setStatus(User.Status.AUTHORIZED);
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

        changeProcessor = new ChangeProcessor(new ChangeProcessorListener<T>(){
            @Override
            public void onComplete(){
                Logger.log(TAG, "Change processor done");
            }

            @Override
            public Ghost onAcknowledged(RemoteChange remoteChange, Change acknowledgedChange)
            throws RemoteChangeInvalidException {
                // if this isn't a removal, update the ghost for the relevant object
                return bucket.acknowledgeChange(remoteChange, acknowledgedChange);
            }
            @Override
            public void onError(RemoteChange remoteChange, Change erroredChange){
                Logger.log(TAG, String.format("We have an error! %s", remoteChange));
            }
            @Override
            public Ghost onRemote(RemoteChange remoteChange)
            throws RemoteChangeInvalidException {
                return bucket.applyRemoteChange(remoteChange);
            }
            
        });
    }

    @Override
    public void reset(){
        changeProcessor.reset();
    }

    private boolean hasChangeVersion(){
        return bucket.hasChangeVersion();
    }

    private String getChangeVersion(){
        return bucket.getChangeVersion();
    }

    private void getLatestVersions(){
        // TODO: should local changes still be stored?
        // abort any remote and local changes since we're getting new data
        // and top the processor
        changeProcessor.abort();
        haveIndex = false;
        // initialize the new query for new index data
        IndexQuery query = new IndexQuery();
        // send the i:::: messages
        sendMessage(query.toString());
    }
    /**
     * Diffs and object's local modifications and queues up the changes
     */
    public Change queueLocalChange(T object){
        Change change = new Change(Change.OPERATION_MODIFY, object);
        changeProcessor.addChange(change);
        return change;
    }

    public Change queueLocalDeletion(T object){
        Change change = new Change(Change.OPERATION_REMOVE, object);
        changeProcessor.addChange(change);
        return change;
    }

    private static final String INDEX_CURRENT_VERSION_KEY = "current";
    private static final String INDEX_VERSIONS_KEY = "index";
    private static final String INDEX_MARK_KEY = "mark";

    private void updateIndex(String indexJson){
        // if we don't have an index processor, create a new one for the associated cv
        // listen for when the index processor is done so we can start the changeprocessor again
        // if we do have an index processor and the cv's match, add the page of items
        // to the queue.
        if (indexJson.equals(RESPONSE_UNKNOWN)) {
            // refresh the index
            getLatestVersions();
            return;
        }
        JSONObject index;
        try {
            index = new JSONObject(indexJson);
        } catch (Exception e) {
            Logger.log(TAG, String.format("Index had invalid json: %s", indexJson));
            return;
        }
        // if we don't have a processor or we are getting a different cv
        if (indexProcessor == null || !indexProcessor.addIndexPage(index)) {
            // make sure we're not processing changes and clear pending changes
            changeProcessor.reset();
            // start a new index
            String currentIndex;
            try {
                currentIndex = index.getString(INDEX_CURRENT_VERSION_KEY);
            } catch(JSONException e){
                // we have an empty index
                currentIndex = "";
            }

            indexProcessor = new IndexProcessor(getBucket(), currentIndex, indexProcessorListener);
            indexProcessor.addIndexPage(index);
        } else {
            // received an index page for a different change version
            // TODO: What do we do now?
            Logger.log(TAG, "Processing index?");
        }

    }

    private IndexProcessorListener indexProcessorListener = new IndexProcessorListener(){
        @Override
        public void onComplete(String cv){
            Logger.log(TAG, String.format("Finished downloading index %s", cv));
            haveIndex = true;
            changeProcessor.start();
        }
    };

    private void handleRemoteChanges(String changesJson){
        JSONArray changes;
        if (changesJson.equals(RESPONSE_UNKNOWN)) {
            Logger.log(TAG, "CV is out of date");
            changeProcessor.reset();
            getLatestVersions();
            return;
        }
        try {
            changes = new JSONArray(changesJson);
        } catch (JSONException e){
            Logger.log(TAG, "Failed to parse remote changes JSON", e);
            return;
        }
        // Loop through each change? Convert changes to array list
        List<Object> changeList = Channel.convertJSON(changes);
        changeProcessor.addChanges(changeList);
        Logger.log(TAG, String.format("Received %d change(s)", changes.length()));
    }

    private static final String ENTITY_DATA_KEY = "data";
    private void handleVersionResponse(String versionData){
        // versionData will be: key.version\n{"data":ENTITY}
        // we need to parse out the key and version, parse the json payload and
        // retrieve the data
        if (indexProcessor == null || !indexProcessor.addObjectData(versionData)) {
          Logger.log(TAG, String.format("Unkown Object for index: %s", versionData));
        }

    }

    public Bucket getBucket(){
        return bucket;
    }

    public User getUser(){
        return bucket.getUser();
    }

    public String getSessionId(){
        return sessionId;
    }

    public boolean isStarted(){
        return started;
    }

    public boolean isConnected(){
        return connected;
    }

    /**
     * Send Bucket's init message to start syncing changes.
     */
    @Override
    public void start(){
        if (started) {
            // we've already started
            return;
        }
        // If socket isn't connected yet we have to wait until connection
        // is up and try starting then
        if (!connected) {
            if (listener != null) listener.onOpen(this);
            startOnConnect = true;
            return;
        }
        if (!bucket.getUser().hasAccessToken()) {
            // we won't connect unless we have an access token
            return;
        }

        started = true;
        stopOnIdle = false;

        Object initialCommand;
        if (!hasChangeVersion()) {
            // the bucket has never gotten an index
            haveIndex = false;
            initialCommand = new IndexQuery();
        } else {
            // retive changes since last cv
            haveIndex = true;
            initialCommand = String.format("%s:%s", COMMAND_VERSION, getChangeVersion());
        }

        // Build the required json object for initializing
        HashMap<String,Object> init = new HashMap<String,Object>(6);
        init.put(FIELD_API_VERSION, 1);
        init.put(FIELD_CLIENT_ID, sessionId);
        init.put(FIELD_APP_ID, appId);
        init.put(FIELD_AUTH_TOKEN, bucket.getUser().getAccessToken());
        init.put(FIELD_BUCKET_NAME, bucket.getRemoteName());
        init.put(FIELD_COMMAND, initialCommand);
        String initParams = new JSONObject(init).toString();
        String message = String.format(COMMAND_FORMAT, COMMAND_INIT, initParams);
        sendMessage(message);
    }

    /**
     * Saves syncing state and tells listener to close
     */
    @Override
    public void stop(){
        startOnConnect = false;
        started = false;
        if (changeProcessor != null) {
            changeProcessor.stop();
        }
        if (listener != null) {
            listener.onClose(this);
        }
    }

    // websocket
    public void onConnect(){
        connected = true;
        if(startOnConnect) start();
    }

    public void onDisconnect(){
        changeProcessor.stop();
        connected = false;
        started = false;
    }
    /**
     * Receive a message from the WebSocketManager which already strips the channel
     * prefix from the message.
     */
    public void receiveMessage(String message){
        // parse the message and react to it
        String[] parts = message.split(":", MESSAGE_PARTS);
        String command = parts[COMMAND_PART];

        run(command, parts[1]);

    }

    // send without the channel id, the socket manager should know which channel is writing
    private void sendMessage(String message){
        // send a message
        if (listener != null) {
            MessageEvent event = new MessageEvent(this, message);
            listener.onMessage(event);
        } else {
            Logger.log(TAG, String.format("No one listening to channel %s", this));
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
                Logger.log(TAG, String.format("Don't know how to run: %s", name));
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
        final private IndexProcessorListener listener;
        int indexedCount = 0;

        public IndexProcessor(Bucket bucket, String cv, IndexProcessorListener listener){
            Logger.log(TAG, String.format("Starting index processor with version: %s for bucket %s", cv, bucket));
            this.bucket = bucket;
            this.cv = cv;
            this.listener = listener;
        }
        public Boolean addObjectData(String versionData){

            String[] objectParts = versionData.split("\n");
            String prefix = objectParts[0];
            int lastDot = prefix.lastIndexOf(".");
            if (lastDot == -1) {
                Logger.log(TAG, String.format("Missing version string: %s", prefix));
                return false;
            }
            String key = prefix.substring(0, lastDot);
            String version = prefix.substring(lastDot + 1);
            String payload = objectParts[1];

            if (payload.equals(RESPONSE_UNKNOWN)) {
                Logger.log(TAG, String.format("Object unkown to simperium: %s.%s", key, version));
                return false;
            }

            ObjectVersion objectVersion = new ObjectVersion(key, Integer.parseInt(version));
            synchronized(index){
                if(!index.remove(objectVersion.toString())){
                    Logger.log(TAG, String.format("Index didn't have %s", objectVersion));
                    return false;
                }
            }
            Logger.log(TAG, String.format("We were waiting for %s.%s", key, version));

            JSONObject data = null;
            try {
                JSONObject payloadJSON = new JSONObject(payload);
                data = payloadJSON.getJSONObject(ENTITY_DATA_KEY);
            } catch (JSONException e) {
                Logger.log(TAG, "Failed to parse object JSON", e);
                return false;
            }

            Integer remoteVersion = Integer.parseInt(version);
            // build the ghost and update
            Map<String,Object> properties = Channel.convertJSON(data);
            Ghost ghost = new Ghost(key, remoteVersion, properties);
            bucket.addObjectWithGhost(ghost);
            indexedCount ++;
            if (complete && index.size() == 0) {
                notifyDone();
            } else if(indexedCount % 10 == 0) {
                notifyProgress();
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
                Logger.log(TAG, String.format("Index did not have current version %s", cv));
                currentIndex = "";
            }

            if (!currentIndex.equals(cv)) {
                return false;
            }

            JSONArray indexVersions;
            try {
                indexVersions = indexPage.getJSONArray(INDEX_VERSIONS_KEY);
            } catch(JSONException e){
                Logger.log(TAG, String.format("Index did not have entities: %s", indexPage));
                return true;
            }
            Logger.log(TAG, String.format("received %d entities", indexVersions.length()));
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
                            index.add(objectVersion.toString());
                            sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, versionNumber));
                        } else {
                            Logger.log(TAG, String.format("Object is up to date: %s", version));
                        }

                    } catch (JSONException e) {
                        Logger.log(TAG, String.format("Error processing index: %d", i), e);
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
        /**
         * If the index is done processing
         */
        public boolean isComplete(){
            return complete;
        }

        private void notifyDone(){
            bucket.indexComplete(cv);
            listener.onComplete(cv);
        }


        private void notifyProgress(){
            bucket.notifyOnNetworkChangeListeners(Bucket.ChangeType.INDEX);
        }
    }

    private interface ChangeProcessorListener<T extends Syncable> {
        /**
         * Received a remote change that acknowledges local change
         */
        public Ghost onAcknowledged(RemoteChange remoteChange, Change change)
            throws RemoteChangeInvalidException;
        /**
         * Received a remote change indicating an error in change request
         */
        public void onError(RemoteChange remoteChange, Change change);
        /**
         * Received a remote change that did not originate locally
         */
        public Ghost onRemote(RemoteChange change)
            throws RemoteChangeInvalidException;
        /**
         * All changes have been processed and entering idle state
         */
        public void onComplete();
    }
    
    public boolean haveCompleteIndex(){
        return haveIndex;
    }

    /**
     * ChangeProcessor should perform operations on a seperate thread as to not block the websocket
     * ideally it will be a FIFO queue processor so as changes are brought in they can be appended.
     * We also need a way to pause and clear the queue when we download a new index.
     */
    private class ChangeProcessor implements Runnable, Change.OnRetryListener {

        // public static final Integer CAPACITY = 200;


        public static final long RETRY_DELAY_MS       = 5000; // 5 seconds for retries?
        private ChangeProcessorListener listener;
        private Thread thread;

        private List<Map<String,Object>> remoteQueue = Collections.synchronizedList(new ArrayList<Map<String,Object>>(10));
        private List<Change> localQueue = Collections.synchronizedList(new ArrayList<Change>());
        private Map<String,Change> pendingChanges = Collections.synchronizedMap(new HashMap<String,Change>());
        private Timer retryTimer;
        
        private final Object lock = new Object();
        public Object runLock = new Object();

        public ChangeProcessor(ChangeProcessorListener listener) {
            this.listener = listener;
            this.retryTimer = new Timer();
            restore();
        }

        /**
         * If thread is running
         */
        public boolean isRunning(){
            return thread != null && thread.isAlive();
        }

        private void restore(){
            synchronized(lock){
                SerializedQueue<T> serialized = serializer.restore(bucket);
                localQueue.addAll(serialized.queued);
                pendingChanges.putAll(serialized.pending);
            }
        }

        public void addChanges(List<Object> changes){
            synchronized(lock){
                Iterator iterator = changes.iterator();
                while(iterator.hasNext()){
                    remoteQueue.add((Map<String,Object>)iterator.next());
                }
            }
            start();
        }

        public void addChange(Map<String,Object> change){
            synchronized(lock){                
                remoteQueue.add(change);
            }
            start();
        }
        /**
         * Local change to be queued
         */
        public void addChange(Change change){
            synchronized (lock){
                // compress all changes for this same key
                Iterator<Change> iterator = localQueue.iterator();
                while(iterator.hasNext()){
                    Change queued = iterator.next();
                    if(queued.getKey().equals(change.getKey())){
                        serializer.onDequeueChange(queued);
                        iterator.remove();
                    }
                }
                serializer.onQueueChange(change);
                localQueue.add(change);
            }
            start();
        }

        public void start(){
            // channel must be started and have complete index
            if (!started || !haveCompleteIndex()) {
                Logger.log(
                    TAG,
                    String.format(
                        "Need an index before processing changes %d remote and %d local changes %d pending",
                        remoteQueue.size(), localQueue.size(), pendingChanges.size()
                    )
                );
                
                return;
            }
            if (thread == null || thread.getState() == Thread.State.TERMINATED) {
                Logger.log(
                    TAG,
                    String.format(
                        "Starting up the change processor with %d remote and %d local changes %d pending",
                        remoteQueue.size(), localQueue.size(), pendingChanges.size()
                    )
                );
                thread = new Thread(this, String.format("simperium.processor.%s", getBucket().getName()));
                thread.start();
            } else {
                // notify
                synchronized(runLock){
                    runLock.notify();
                }
            }
        }

        public void stop(){
            // interrupt the thread
            if (this.thread != null) {
                this.thread.interrupt();
                synchronized(runLock){
                    runLock.notify();
                }
            }
        }

        protected void reset(){
            pendingChanges.clear();
            serializer.reset(bucket);
        }

        protected void abort(){
            reset();
            stop();
        }

        protected boolean hasPendingChanges(){
            synchronized(lock){
                return !pendingChanges.isEmpty() || !localQueue.isEmpty();
            }
        }

        public void run(){
            Logger.log(TAG, String.format("%s - Starting change queue", Thread.currentThread().getName()));
            while(true && !Thread.interrupted()){
                // TODO: only process one remote change at a time
                processRemoteChanges();
                // TODO: only process one local change at a time
                processLocalChanges();
                if(!hasPendingChanges()){
                    synchronized(runLock){
                        try {
                            runLock.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }
            Logger.log(TAG, String.format("%s - Queue interrupted", Thread.currentThread().getName()));
        }

        private void processRemoteChanges(){
            synchronized(lock){
                // bail if thread is interrupted
                while(remoteQueue.size() > 0 && !Thread.interrupted()){
                    // take an item off the queue
                    RemoteChange remoteChange = RemoteChange.buildFromMap(remoteQueue.remove(0));
                    Logger.log(TAG, String.format("Received remote change with cv: %s", remoteChange.getChangeVersion()));
                    Boolean acknowledged = false;
                    // synchronizing on pendingChanges since we're looking up and potentially
                    // removing an entry
                    Change change = null;
                    change = pendingChanges.get(remoteChange.getKey());
                    if (remoteChange.isAcknowledgedBy(change)) {
                        // change is no longer pending so remove it
                        pendingChanges.remove(change.getKey());
                        if (remoteChange.isError()) {
                            Logger.log(TAG, String.format("Change error response! %d %s", remoteChange.getErrorCode(), remoteChange.getKey()));
                            // TODO: determine if we can retry this change by reapplying
                            listener.onError(remoteChange, change);
                        } else {
                            try {
                                Ghost ghost = listener.onAcknowledged(remoteChange, change);
                                serializer.onAcknowledgeChange(change);
                                Change compressed = null;
                                Iterator<Change> queuedChanges = localQueue.iterator();
                                while(queuedChanges.hasNext()){
                                    Change queuedChange = queuedChanges.next();
                                    if (queuedChange.getKey().equals(change.getKey())) {
                                        queuedChanges.remove();
                                        Logger.log(String.format("Compressed queued local change for %s", queuedChange.getKey()));
                                        compressed = queuedChange.reapplyOrigin(ghost.getVersion(), ghost.getDiffableValue());
                                    }
                                }
                                if (compressed != null) {
                                    localQueue.add(compressed);
                                }
                            } catch (RemoteChangeInvalidException e){
                                Logger.log(TAG, "Remote change could not be acknowledged", e);
                            }
                        }
                    } else {
                        if (remoteChange.isError()){
                            Logger.log(TAG, String.format("Remote change %s was an error but not acknowledged", remoteChange));
                        } else {
                            try {
                                listener.onRemote(remoteChange);
                            } catch (RemoteChangeInvalidException e) {
                                Logger.log(TAG, "Remote change could not be applied", e);
                            }
                        }
                    }
                    if (!remoteChange.isError() && remoteChange.isRemoveOperation()) {
                        Iterator<Change> iterator = localQueue.iterator();
                        while(iterator.hasNext()){
                            Change queuedChange = iterator.next();
                            if (queuedChange.getKey().equals(remoteChange.getKey())) {
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }
        
        public void processLocalChanges(){
            synchronized(lock){
                if (localQueue.isEmpty()) {
                    return;
                }
                final List<Change> sendLater = new ArrayList<Change>();
                // find the first local change whose key does not exist in the pendingChanges and there are no remote changes
                while(!localQueue.isEmpty() && !Thread.interrupted()){
                    // take the first change of the queue
                    Change localChange = localQueue.remove(0);
                        // check if there's a pending change with the same key
                    if (pendingChanges.containsKey(localChange.getKey())) {
                        // we have a change for this key that has not been acked
                        // so send it later
                        Logger.log(TAG, String.format("Changes pending for %s re-queueing %s", localChange.getKey(), localChange.getChangeId()));
                        sendLater.add(localChange);
                        // let's get the next change
                    } else {
                        // send the change to simperium, if the change ends up being empty
                        // then we'll just skip it
                        if(sendChange(localChange)) {
                            // add the change to pending changes
                            pendingChanges.put(localChange.getKey(), localChange);
                            localChange.setOnRetryListener(this);
                            // starts up the timer
                            this.retryTimer.scheduleAtFixedRate(localChange.getRetryTimer(), RETRY_DELAY_MS, RETRY_DELAY_MS);
                        }
                    }
                }
                localQueue.addAll(0, sendLater);
            }
        }

        @Override
        public void onRetry(Change change){
            sendChange(change);
        }

        private Boolean sendChange(Change change){
            // send the change down the socket!
            if (!connected) {
                // channel is not initialized, send on reconnect
                Logger.log(TAG, String.format("Abort sending change, channel not initialized: %s", change.getChangeId()));
                return true;
            }
            Logger.log(TAG, String.format("send change ccid %s", change.getChangeId()));

            Map<String,Object> map = new HashMap<String,Object>(3);
            map.put(Change.ID_KEY, change.getKey());
            map.put(Change.CHANGE_ID_KEY, change.getChangeId());
            map.put(JSONDiff.DIFF_OPERATION_KEY, change.getOperation());
            Integer version = change.getVersion();
            if (version != null && version > 0) {
                map.put(Change.SOURCE_VERSION_KEY, version);
            }

            if (change.requiresDiff()) {
                Map<String,Object> diff = change.getDiff(); // jsondiff.diff(change.getOrigin(), change.getTarget());
                if (diff.isEmpty()) {
                    Logger.log(TAG, String.format("Discarding empty change %s diff: %s", change.getChangeId(), diff));
                    change.setComplete();
                    return false;
                }
                map.put(JSONDiff.DIFF_VALUE_KEY, diff.get(JSONDiff.DIFF_VALUE_KEY));
            }
            JSONObject changeJSON = Channel.serializeJSON(map);

            sendMessage(String.format("c:%s", changeJSON.toString()));
            serializer.onSendChange(change);
            change.setSent();
            return true;
        }

    }

    public static Map<String,Object> convertJSON(JSONObject json){
        Map<String,Object> map = new HashMap<String,Object>(json.length());
        Iterator keys = json.keys();
        while(keys.hasNext()){
            String key = (String)keys.next();
            try {
                Object val = json.get(key);
                // Logger.log(String.format("Hello! %s", json.get(key).getClass().getName()));
                if (val.getClass().equals(JSONObject.class)) {
                    map.put(key, convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    map.put(key, convertJSON((JSONArray) val));
                } else {
                    map.put(key, val);
                }
            } catch (JSONException e) {
                Logger.log(TAG, String.format("Failed to convert JSON: %s", e.getMessage()), e);
            }
        }
        return map;
    }

    public static List<Object> convertJSON(JSONArray json){
        List<Object> list = new ArrayList<Object>(json.length());
        for (int i=0; i<json.length(); i++) {
            try {
                Object val = json.get(i);
                if (val.getClass().equals(JSONObject.class)) {
                    list.add(convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    list.add(convertJSON((JSONArray) val));
                } else {
                    list.add(val);
                }
            } catch (JSONException e) {
                Logger.log(TAG, String.format("Faile to convert JSON: %s", e.getMessage()), e);
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
                } else if(val instanceof Change){
                    json.put(key, serializeJSON(((Change) val).toJSONSerializable()));
                } else {
                    json.put(key, val);
                }
            } catch(JSONException e){
               Logger.log(TAG, String.format("Failed to serialize %s", val));
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
            } else if(val instanceof Change){
                json.put(serializeJSON(((Change) val).toJSONSerializable()));
            } else {
                json.put(val);
            }
        }
        return json;
    }

}