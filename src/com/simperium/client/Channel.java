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

import com.simperium.client.Simperium;
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

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;

import android.os.Handler;
import android.os.HandlerThread;

public class Channel<T extends Syncable> {
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
    private OnMessageListener listener;
    // track channel status
    private boolean started = false, connected = false, startOnConnect = false;
    private CommandInvoker commands = new CommandInvoker();
    private String appId;
    private JSONDiff jsondiff = new JSONDiff();
    private Context context;

    // for sending and receiving changes
    final private ChangeProcessor changeProcessor;
    private IndexProcessor indexProcessor;

    public Channel(Context context, String appId, final Bucket<T> bucket, User user, OnMessageListener listener){
        this.context = context;
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

        changeProcessor = new ChangeProcessor(new ChangeProcessorListener<T>(){
            public void onComplete(){
            }
            public void onAcknowledgedChange(String cv, String key, T object){
                Logger.log(String.format("Acknowledging change: %s", object));
                getBucket().updateObjectWithGhost(cv, object.getGhost());
            }
            public void onAddObject(String cv, String key, T object){
                getBucket().addObjectWithGhost(cv, object.getGhost());
            }
            public void onUpdateObject(String cv, String key, T object){
                getBucket().updateObjectWithGhost(cv, object.getGhost());
            }
            public void onRemoveObject(String changeVersion, String key){
                getBucket().removeObjectWithKey(changeVersion, key);
            }
        });
    }
        
    protected void reset(){
        changeProcessor.reset();
        if (started) {
            getLatestVersions();            
        } else {
            startOnConnect = true;  
        }
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
        // TODO: should local changes still be stored?
        // abort any remote and local changes since we're getting new data
        // and top the processor
        changeProcessor.abort();
        // initialize the new query for new index data
        IndexQuery query = new IndexQuery();
        // send the i:::: messages
        sendMessage(query.toString());
    }
    /**
     * Diffs and object's local modifications and queues up the changes
     */
    protected Change queueLocalChange(T object){
        Change<T> change = new Change<T>(Change.OPERATION_MODIFY, object);
        changeProcessor.addChange(change);
        return change;
    }

    protected Change queueLocalDeletion(T object){
        Change<T> change = new Change<T>(Change.OPERATION_REMOVE, object);
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
            Logger.log(String.format("Index had invalid json: %s", indexJson));
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
            Logger.log("Processing index?");
        }

    }
    
    private IndexProcessorListener indexProcessorListener = new IndexProcessorListener(){
        @Override
        public void onComplete(String cv){
            Logger.log(String.format("Finished downloading index %s", cv));
            changeProcessor.start();
        }
    };

    private void handleRemoteChanges(String changesJson){
        JSONArray changes;
        if (changesJson.equals(RESPONSE_UNKNOWN)) {
            Logger.log("CV is out of date");
            changeProcessor.reset();
            getLatestVersions();
            return;
        }
        try {
            changes = new JSONArray(changesJson);
        } catch (JSONException e){
            Logger.log("Failed to parse remote changes JSON", e);
            return;
        }
        // Loop through each change? Convert changes to array list
        List<Object> changeList = Channel.convertJSON(changes);
        changeProcessor.addChanges(changeList);
        Logger.log(String.format("Received %d change(s)", changes.length()));
    }

    private static final String ENTITY_DATA_KEY = "data";
    private void handleVersionResponse(String versionData){
        // versionData will be: key.version\n{"data":ENTITY}
        // we need to parse out the key and version, parse the json payload and
        // retrieve the data
        if (indexProcessor == null || !indexProcessor.addObjectData(versionData)) {
          Logger.log(String.format("Unkown Object for index: %s", versionData));
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
        init.put(FIELD_CLIENT_ID, Simperium.CLIENT_ID);
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
        MessageEvent event = new MessageEvent(this, message);
        emit(event);
    }

    private void emit(MessageEvent event){
        if (listener != null) {
            listener.onMessage(event);
        } else {
            Logger.log(String.format("No one listening to channel %s", this));
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

    public interface OnMessageListener {
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
                Logger.log(String.format("Don't know how to run: %s", name));
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
            Logger.log(String.format("Starting index processor with version: %s for bucket %s", cv, bucket));
            this.bucket = bucket;
            this.cv = cv;
            this.listener = listener;
        }
        public Boolean addObjectData(String versionData){

            String[] objectParts = versionData.split("\n");
            String prefix = objectParts[0];
            int lastDot = prefix.lastIndexOf(".");
            if (lastDot == -1) {
                Logger.log(String.format("Missing version string: %s", prefix));
                return false;
            }
            String key = prefix.substring(0, lastDot);
            String version = prefix.substring(lastDot + 1);
            String payload = objectParts[1];

            if (payload.equals(RESPONSE_UNKNOWN)) {
                Logger.log(String.format("Object unkown to simperium: %s.%s", key, version));
                return false;
            }

            ObjectVersion objectVersion = new ObjectVersion(key, Integer.parseInt(version));
            synchronized(index){
                if(!index.remove(objectVersion.toString())){
                    Logger.log(String.format("Index didn't have %s", objectVersion));
                    return false;
                }
            }
            Logger.log(String.format("We were waiting for %s.%s", key, version));

            JSONObject data = null;
            try {
                JSONObject payloadJSON = new JSONObject(payload);
                data = payloadJSON.getJSONObject(ENTITY_DATA_KEY);
            } catch (JSONException e) {
                Logger.log("Failed to parse object JSON", e);
                return false;
            }

            Integer remoteVersion = Integer.parseInt(version);
            // build the ghost and update
            Map<String,Object> properties = Channel.convertJSON(data);
            Ghost ghost = new Ghost(key, remoteVersion, properties);
            bucket.addObjectWithGhost(ghost);

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
                Logger.log(String.format("Index did not have current version %s", cv));
                currentIndex = "";
            }

            if (!currentIndex.equals(cv)) {
                return false;
            }

            JSONArray indexVersions;
            try {
                indexVersions = indexPage.getJSONArray(INDEX_VERSIONS_KEY);
            } catch(JSONException e){
                Logger.log(String.format("Index did not have entities: %s", indexPage));
                return true;
            }
            Logger.log(String.format("received %d entities", indexVersions.length()));
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
                            sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, versionNumber));
                        } else {
                            Logger.log(String.format("Object is up to date: %s", version));
                        }

                    } catch (JSONException e) {
                        Logger.log(String.format("Error processing index: %d", i), e);
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
            bucket.setChangeVersion(cv);
            listener.onComplete(cv);
        }

    }

    private interface ChangeProcessorListener<T extends Syncable> {
        /**
         * Change has been applied.
         */
        void onAcknowledgedChange(String changeVersion, String key, T object);
        void onUpdateObject(String changeVersion, String key, T object);
        void onAddObject(String changeVersion, String key, T object);
        void onRemoveObject(String changeVersion, String key);
        /**
         * All changes have been processed and entering idle state
         */
        void onComplete();
    }
    
    private boolean haveCompleteIndex(){
        return indexProcessor != null && indexProcessor.isComplete();
    }

    /**
     * ChangeProcessor should perform operations on a seperate thread as to not block the websocket
     * ideally it will be a FIFO queue processor so as changes are brought in they can be appended.
     * We also need a way to pause and clear the queue when we download a new index.
     */
    private class ChangeProcessor implements Runnable, Change.OnRetryListener<T> {

        // public static final Integer CAPACITY = 200;


        public static final long RETRY_DELAY_MS       = 5000; // 5 seconds for retries?
        public static final String PENDING_KEY = "pending";
        public static final String QUEUED_KEY = "queued";

        private JSONDiff diff = new JSONDiff();
        private ChangeProcessorListener listener;
        private Thread thread;
        // private ArrayBlockingQueue<Map<String,Object>> queue;
        private List<Map<String,Object>> remoteQueue;
        private List<Change> localQueue;
        private Map<String,Change> pendingChanges;
        private Handler handler;
        private Change pendingChange;
        private Timer retryTimer;
        
        private final Object lock = new Object();

        public ChangeProcessor(ChangeProcessorListener listener) {
            this.listener = listener;
            this.remoteQueue = Collections.synchronizedList(new ArrayList<Map<String,Object>>());
            this.localQueue = Collections.synchronizedList(new ArrayList<Change>());
            this.pendingChanges = Collections.synchronizedMap(new HashMap<String,Change>());
            String handlerThreadName = String.format("channel-handler-%s", bucket.getName());
            HandlerThread handlerThread = new HandlerThread(handlerThreadName);
            handlerThread.start();
            this.handler = new Handler(handlerThread.getLooper());
            Logger.log(String.format("Starting change processor handler on thread %s", this.handler.getLooper().getThread().getName()));
            this.retryTimer = new Timer();
            restore();
        }
        private String getFileName(){
            return String.format("simperium-queue-%s-%s.json", bucket.getRemoteName(), user.getAccessToken());
        }
        private void save(){
            try {
                saveToFile();
            } catch (java.io.IOException e) {
                Logger.log(String.format("Could not serialize change processor queue to file %s", getFileName()), e);
            }
        }
        /**
         * Save state of pending and locally queued items
         */
        private void saveToFile() throws java.io.IOException {
            //  construct JSON string of pending and local queue
            Logger.log(String.format("Saving to file %s", getFileName()));
            synchronized(lock){
                FileOutputStream stream = null;
                try {
                    stream = context.openFileOutput(getFileName(), Context.MODE_PRIVATE);
                    Map<String,Object> serialized = new HashMap<String,Object>(2);
                    serialized.put(PENDING_KEY, pendingChanges);
                    serialized.put(QUEUED_KEY, localQueue);
                    JSONObject json = Channel.serializeJSON(serialized);
                    String jsonString = json.toString();
                    Logger.log(String.format("Saving: %s", jsonString));
                    stream.write(jsonString.getBytes(), 0, jsonString.length());
                } finally {
                    if(stream !=null) stream.close();
                }
            }
        }
        
        private void restore(){
            // don't restore unless we have a user
            if (user == null || !user.hasAccessToken()) return;
            
            try {
                restoreFromFile();
            } catch (Exception e) {
                Logger.log(String.format("Could not restore from file: %s", getFileName()), e);
            }
            Logger.log(
                String.format(
                    "Restored change processwor with %d remote and %d local changes %d pending",
                    remoteQueue.size(), localQueue.size(), pendingChanges.size()
                )
            );
            
        }
        /**
         * 
         */
        private void restoreFromFile() throws java.io.IOException, org.json.JSONException {
            // read JSON string and reconstruct
            synchronized(lock){
                BufferedInputStream stream = null;
                try {
                    stream = new BufferedInputStream(context.openFileInput(getFileName()));
                    byte[] contents = new byte[1024];
                    int bytesRead = 0;
                    StringBuilder builder = new StringBuilder();
                    while((bytesRead = stream.read(contents)) != -1){
                        builder.append(new String(contents, 0, bytesRead));
                    }
                    JSONObject json = new JSONObject(builder.toString());
                    Map<String,Object> changeData = Channel.convertJSON(json);
                    Logger.log(String.format("We have changes from serialized file %s", changeData));
                    
                    if (changeData.containsKey(PENDING_KEY)) {
                        Map<String,Map<String,Object>> pendingData = (Map<String,Map<String,Object>>)changeData.get(PENDING_KEY);
                        Iterator<Map.Entry<String,Map<String,Object>>> pendingEntries = pendingData.entrySet().iterator();
                        while(pendingEntries.hasNext()){
                            Map.Entry<String, Map<String,Object>> entry = pendingEntries.next();
                            T object = bucket.get(entry.getKey());
                            Change<T> change = Change.buildChange(object, entry.getValue());
                            pendingChanges.put(entry.getKey(), change);
                        }
                    }
                    if (changeData.containsKey(QUEUED_KEY)) {
                        List<Map<String,Object>> queuedData = (List<Map<String,Object>>)changeData.get(QUEUED_KEY);
                        Iterator<Map<String,Object>> queuedItems = queuedData.iterator();
                        while(queuedItems.hasNext()){
                            Map<String,Object> queuedItem = queuedItems.next();
                            String key = (String) queuedItem.get(Change.ID_KEY);
                            T object = bucket.get(key);
                            localQueue.add(Change.buildChange(object, queuedItem));
                        }
                    }
                    
                } finally {
                    if(stream != null) stream.close();
                }
            }
        }
        /**
         * 
         */
        private void clearFile(){
            context.deleteFile(getFileName());
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
                        iterator.remove();
                    }
                }
                localQueue.add(change);
            }
            save();
            start();
        }

        public void start(){
            // channel must be started and have complete index
            if (!started || !haveCompleteIndex()) {
                Logger.log(
                    String.format(
                        "Need an index before processing changes %d remote and %d local changes %d pending",
                        remoteQueue.size(), localQueue.size(), pendingChanges.size()
                    )
                );
                
                return;
            }
            if (thread == null || thread.getState() == Thread.State.TERMINATED) {
                Logger.log(
                    String.format(
                        "Starting up the change processor with %d remote and %d local changes %d pending",
                        remoteQueue.size(), localQueue.size(), pendingChanges.size()
                    )
                );
                thread = new Thread(this);
                thread.start();
            } else {
                Logger.log("Didn't start thread");
            }
        }

        public void stop(){
            // interrupt the thread
            if (this.thread != null) {
                this.thread.interrupt();
            }
        }

        protected void reset(){
            pendingChanges.clear();
            clearFile();
        }

        protected void abort(){
            reset();
            stop();
        }


        public void run(){
            // apply any remote changes we have
            processRemoteChanges();
            // if we have a change to an object that is waiting for an ack
            processLocalChanges();
            Logger.log(
                String.format(
                    "Done processing thread with %d remote and %d local changes %d pending",
                    remoteQueue.size(), localQueue.size(), pendingChanges.size()
                )
            );
        }

        private void processRemoteChanges(){
            synchronized(lock){
                // bail if thread is interrupted
                while(remoteQueue.size() > 0 && !Thread.interrupted()){
                    // take an item off the queue
                    RemoteChange remoteChange = RemoteChange.buildFromMap(remoteQueue.remove(0));
                    Logger.log(String.format("Received remote change with cv: %s", remoteChange.getChangeVersion()));
                    Boolean acknowledged = false;
                    // synchronizing on pendingChanges since we're looking up and potentially
                    // removing an entry
                    Change change = null;
                    synchronized(pendingChanges){
                        change = pendingChanges.get(remoteChange.getKey());
                        if (remoteChange.isAcknowledgedBy(change)) {
                            // change is no longer pending so remove it
                            pendingChanges.remove(change.getKey());
                            if (remoteChange.isError()) {
                                Logger.log(String.format("Change error response! %d %s", remoteChange.getErrorCode(), remoteChange.getKey()));
                                // TODO: determine if we can retry this change by reapplying
                            }
                        }
                    }
                    // apply the remote change
                    applyRemoteChange(remoteChange);
                }
            }
        }
        
        public void processLocalChanges(){
            final List<Change> sendLater = new ArrayList<Change>();
            synchronized(lock){
                // find the first local change whose key does not exist in the pendingChanges
                while(localQueue.size() > 0 && !Thread.interrupted()){
                    // take the first change of the queue
                    Change localChange = localQueue.remove(0);
                        // check if there's a pending change with the same key
                    if (pendingChanges.containsKey(localChange.getKey())) {
                        // we have a change for this key that has not been acked
                        // so send it later
                        Logger.log(String.format("Changes pending for %s re-queueing %s", localChange.getKey(), localChange.getChangeId()));
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
            }
            // add the sendLater changes back on top of the queue
            synchronized(lock){
                localQueue.addAll(0, sendLater);
            }
            save();
        }

        @Override
        public void onRetry(Change<T> change){
            sendChange(change);
        }

        private Boolean sendChange(Change<T> change){
            // send the change down the socket!
            if (!connected) {
                // channel is not initialized, send on reconnect
                Logger.log(String.format("Abort sending change, channel not initialized: %s", change.getChangeId()));
                return true;
            }
            Logger.log(String.format("*** Send local change: %s", change.getChangeId()));

            Map<String,Object> map = new HashMap<String,Object>(3);
            map.put(Change.ID_KEY, change.getKey());
            map.put(Change.CHANGE_ID_KEY, change.getChangeId());
            map.put(JSONDiff.DIFF_OPERATION_KEY, change.getOperation());
            Integer version = change.getVersion();
            if (version != null && version > 0) {
                map.put(Change.SOURCE_VERSION_KEY, version);
            }

            if (change.requiresDiff()) {
                Map<String,Object> diff = jsondiff.diff(change.getOrigin(), change.getTarget());
                if (diff.isEmpty()) {
                    return false;
                }
                Map<String,Object> patch = (Map<String,Object>) diff.get(JSONDiff.DIFF_VALUE_KEY);
                map.put(JSONDiff.DIFF_VALUE_KEY, patch);
            }
            JSONObject changeJSON = Channel.serializeJSON(map);

            sendMessage(String.format("c:%s", changeJSON.toString()));
            return true;
        }

        private void applyRemoteChange(final RemoteChange change){
            final ChangeProcessorListener changeListener = listener;
            if (change == null) return;
            //TODO: error responses that can be retried should be retried here?
            if (change.isError()) return;
            // Remove operation, only notify if it's not an ACK
            if (change.isRemoveOperation()) {
                // remove all queued changes that reference this same object
                synchronized (lock){
                    Iterator<Change> iterator = localQueue.iterator();
                    while(iterator.hasNext()){
                        Change queuedChange = iterator.next();
                        if (queuedChange.getKey().equals(change.getKey())) {
                            iterator.remove();
                        }
                    }
                }
                handler.post(new Runnable(){
                    public void run(){
                        changeListener.onRemoveObject(change.getChangeVersion(), change.getKey());
                        change.setApplied();
                    }
                });
                return;
            }
            final T object;
            if (change.isAddOperation() && !change.isAcknowledged()) {
                object = bucket.buildObject(change.getKey());
            } else {
                object = bucket.getObject(change.getKey());
                if (null == object) {
                   Logger.log(String.format("Local object missing: %s", change.getKey()));
                   return;
                }
                // we need to check if we have the correct version
                // TODO: handle how to sync if source version doesn't match local object
                if (!object.isNew() && !change.getSourceVersion().equals(object.getVersion())) {
                    Logger.log(String.format("Local version %d of object does not match sv: %s %d", object.getVersion(), change.getKey(), change.getSourceVersion()));
                    return;
                }
            }
            Ghost ghost = new Ghost(
                object.getSimperiumKey(),
                change.getObjectVersion(),
                jsondiff.apply(object.getDiffableValue(), change.getPatch())
            );
            object.setGhost(ghost);
            // Compress all queued changes for the same object into a single change
            synchronized (lock){
                Change compressed = null;
                Iterator<Change> queuedChanges = localQueue.iterator();
                while(queuedChanges.hasNext()){
                    Change queuedChange = queuedChanges.next();
                    if (queuedChange.getKey().equals(change.getKey())) {
                        queuedChanges.remove();
                        Logger.log(String.format("Compressed queued local change for %s", queuedChange.getKey()));
                        compressed = queuedChange.reapplyOrigin(object.getVersion(), object.getUnmodifiedValue());
                    }
                }
                if (compressed != null) {
                    localQueue.add(compressed);
                }
            }
            
            // notify the listener
            final boolean isNew = object.isNew() && !change.isAcknowledged();
            this.handler.post(new Runnable(){
                public void run(){
                    if(isNew){
                        changeListener.onAddObject(change.getChangeVersion(), change.getKey(), object);
                    } else {
                        changeListener.onUpdateObject(change.getChangeVersion(), change.getKey(), object);
                    }
                    change.setApplied();
                }
            });

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
                Logger.log(String.format("Failed to convert JSON: %s", e.getMessage()), e);
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
                Logger.log(String.format("Faile to convert JSON: %s", e.getMessage()), e);
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
               Logger.log(String.format("Failed to serialize %s", val));
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