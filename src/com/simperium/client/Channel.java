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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
    private Context context;

    // for sending and receiving changes
    final private ChangeProcessor changeProcessor;
    private IndexProcessor indexProcessor;

    public Channel(Context context, String appId, final Bucket<T> bucket, User user, Listener listener){
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

        changeProcessor = new ChangeProcessor(getBucket(), new ChangeProcessorListener(){
            public void onComplete(){
            }
			public void onAcknowledgedChange(String cv, String key, Bucket.Ghost object){
				Simperium.log(String.format("Acknowledging change: %s", object));
				getBucket().updateObjectWithGhost(cv, object);
			}
            public void onAddObject(String cv, String key, Bucket.Ghost object){
                getBucket().addObjectWithGhost(cv, object);
            }
            public void onUpdateObject(String cv, String key, Bucket.Ghost object){
                getBucket().updateObjectWithGhost(cv, object);
            }
            public void onRemoveObject(String changeVersion, String key){
                getBucket().removeObjectWithKey(changeVersion, key);
            }
        });
    }
	
	protected void reset(){
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
    protected void queueLocalChange(T object){
        // diff the object with it's ghost
		Simperium.log(String.format("Object: %s", object.getDiffableValue()));
		Simperium.log(String.format("Ghost: %s", object.getGhost().getDiffableValue()));
        Map<String,Object> diff = jsondiff.diff(object.getUnmodifiedValue(), object.getDiffableValue());
		Simperium.log(String.format("Diff: %s", diff));

        Change change = new Change(Change.OPERATION_MODIFY, object.getSimperiumKey(), object.getVersion(), object.getUnmodifiedValue(), object.getDiffableValue());
        changeProcessor.addChange(change);

    }

    protected void queueLocalDeletion(T object){
        Change change = new Change(Change.OPERATION_REMOVE, object.getSimperiumKey());
        changeProcessor.addChange(change);
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
                    Simperium.log(String.format("Finished downloading index %s", cv));
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
          Simperium.log(String.format("Unkown Object for index: %s", versionData));
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
    protected void onConnect(){
        connected = true;
        if(startOnConnect) start();
    }

    protected void onDisconnect(){
        changeProcessor.stop();
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
			// build the ghost and update
            Map<String,Object> properties = Channel.convertJSON(data);
			Bucket.Ghost ghost = new Bucket.Ghost(key, remoteVersion, properties);
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

    private interface ChangeProcessorListener {
        /**
         * Change has been applied.
         */
		void onAcknowledgedChange(String changeVersion, String key, Bucket.Ghost object);
        void onUpdateObject(String changeVersion, String key, Bucket.Ghost object);
        void onAddObject(String changeVersion, String key, Bucket.Ghost object);
        void onRemoveObject(String changeVersion, String key);
        /**
         * All changes have been processed and entering idle state
         */
        void onComplete();
    }

    public interface OnChangeRetryListener {
        public void onRetryChange(Change change);
    }
    private static class Change extends TimerTask {
        private String operation;
        private String key;
        private Integer version;
        private Map<String,Object> origin;
        private Map<String,Object> target;
        private String ccid;
        private OnChangeRetryListener listener;

        public static final String OPERATION_MODIFY   = "M";
        public static final String OPERATION_REMOVE   = JSONDiff.OPERATION_REMOVE;
        public static final String ID_KEY             = "id";
        public static final String CHANGE_ID_KEY      = "ccid";
        public static final String SOURCE_VERSION_KEY = "sv";
        public static final String TARGET_KEY         = "target";
        public static final String ORIGIN_KEY         = "origin";
        public static final String OPERATION_KEY      = "o";
        
        /**
         * Constructs a change object from a map of values
         */
        protected static Change buildChange(Map<String,Object> properties){
            return new Change(
                (String)  properties.get(OPERATION_KEY),
                (String)  properties.get(ID_KEY),
                (Integer) properties.get(SOURCE_VERSION_KEY),
                (Map<String,Object>) properties.get(ORIGIN_KEY),
                (Map<String,Object>) properties.get(TARGET_KEY)
            );
        }
        
        public Change(String operation, String key){
            this(operation, key, null, null, null);
        }

        public Change(String operation, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target){
            super();
            this.operation = operation;
            this.key = key;
            this.version = sourceVersion;
            this.origin = Bucket.deepCopy(origin);
            this.target = Bucket.deepCopy(target);
            this.ccid = Simperium.uuid();
        }
        public void stopRetryTimer(){
            cancel();
        }
        public void setOnChangeRetryListener(OnChangeRetryListener listener){
            this.listener = listener;
        }
        public void run(){
            Simperium.log(String.format("Retry change: %s", getChangeId()));
            listener.onRetryChange(this);
        }
        public boolean keyMatches(Change otherChange){
            return otherChange.getKey().equals(getKey());
        }
        public String getKey(){
            return key;
        }
        public String getChangeId(){
            return this.ccid;
        }
        public Map<String,Object> getOrigin(){
            return origin;
        }
        public Map<String,Object> getTarget(){
            return target;
        }
        public String getOperation(){
            return operation;
        }
        public Integer getVersion(){
            return version;
        }
        /**
         * The change message requires a diff value in the JSON payload
         */
        public Boolean requiresDiff(){
            return operation.equals(OPERATION_MODIFY);
        }
        /**
         * Creates a new change with the given sourceVersion and origin
         */
        public Change reapplyOrigin(Integer sourceVersion, Map<String,Object> origin){
            return new Change(operation, key, sourceVersion, origin, target);
        }
        
        protected Map<String,Object> toJSONSerializable(){
            Map<String,Object> props = new HashMap<String,Object>(3);
            // key, version, origin, target, ccid
            props.put(OPERATION_KEY, operation);
            props.put(ID_KEY, key);
            props.put(CHANGE_ID_KEY, ccid);
            if (version != null) {
                props.put(SOURCE_VERSION_KEY, version);
            }
            if (operation.equals(OPERATION_MODIFY)) {
                props.put(ORIGIN_KEY, origin);
                props.put(TARGET_KEY, target);
            }
            return props;
            
        }

    }
    /**
     * Encapsulates parsing and logic for remote changes
     */
    private static class RemoteChange {
        public static final String ID_KEY             = "id";
        public static final String CLIENT_KEY         = "clientid";
        public static final String ERROR_KEY          = "error";
        public static final String ENTITY_VERSION_KEY = "ev";
        public static final String SOURCE_VERSION_KEY = "sv";
        public static final String CHANGE_VERSION_KEY = "cv";
        public static final String CHANGE_IDS_KEY     = "ccids";
        public static final String OPERATION_KEY      = JSONDiff.DIFF_OPERATION_KEY;
        public static final String VALUE_KEY          = JSONDiff.DIFF_VALUE_KEY;
        public static final String OPERATION_MODIFY   = "M";
        public static final String OPERATION_REMOVE   = JSONDiff.OPERATION_REMOVE;

        private String key;
        private String clientid;
        private List<String> ccids;
        private Integer sourceVersion;
        private Integer entityVersion;
        private String changeVersion;
        private String operation;
        private Map<String,Object> value;
        private Integer errorCode;
        /**
         * All remote changes include clientid, key and ccids then these differences:
         * - errors have error key
         * - changes with operation "-" do not have a value
         * - changes with operation "M" have a v (value), ev (entity version) and if not a new object an sv (source version)
         */
        public RemoteChange(String clientid, String key, List<String> ccids, String changeVersion, Integer sourceVersion, Integer entityVersion, String operation, Map<String,Object> value){
            this.clientid = clientid;
            this.key = key;
            this.ccids = ccids;
            this.sourceVersion = sourceVersion;
            this.entityVersion = entityVersion;
            this.operation = operation;
            this.value = value;
            this.changeVersion = changeVersion;
        }

        public RemoteChange(String clientid, String key, List<String> ccids, Integer errorCode){
            this.clientid = clientid;
            this.key = key;
            this.ccids = ccids;
            this.errorCode = errorCode;
        }

        public boolean isAcknowledgedBy(Change change){
            return hasChangeId(change) && getClientId().equals(Simperium.CLIENT_ID);
        }

        public boolean isError(){
            return errorCode != null;
        }

        public boolean isRemoveOperation(){
            return operation.equals(OPERATION_REMOVE);
        }

        public boolean isModifyOperation(){
            return operation.equals(OPERATION_MODIFY);
        }

        public boolean isAddOperation(){
            return isModifyOperation() && sourceVersion == null;
        }

        public Integer getErrorCode(){
            return errorCode;
        }

        public boolean isNew(){
            return !isError() && sourceVersion == null;
        }

        public String getKey(){
            return key;
        }

        public String getClientId(){
            return clientid;
        }

        public Integer getSourceVersion(){
            return sourceVersion;
        }

        public Integer getObjectVersion(){
            return entityVersion;
        }

        public String getChangeVersion(){
            return changeVersion;
        }

        public Map<String,Object> getPatch(){
            return value;
        }

        public boolean hasChangeId(String ccid){
            return ccids.contains(ccid);
        }

        public boolean hasChangeId(Change change){
            return hasChangeId(change.getChangeId());
        }

        public static RemoteChange buildFromMap(Map<String,Object> changeData){
            // get the list of ccids that this applies to
            List<String> ccids = (List<String>)changeData.get(CHANGE_IDS_KEY);
            // get the client id
            String client_id = (String)changeData.get(CLIENT_KEY);
            // get the id of the object it applies to
            String id = (String)changeData.get(ID_KEY);
            if (changeData.containsKey(ERROR_KEY)) {
                Integer errorCode = (Integer)changeData.get(ERROR_KEY);
                Simperium.log(String.format("Received error for change: %d", errorCode, changeData));
                return new RemoteChange(client_id, id, ccids, errorCode);
            }
            String operation = (String)changeData.get(OPERATION_KEY);
            Integer sourceVersion = (Integer)changeData.get(SOURCE_VERSION_KEY);
            Integer objectVersion = (Integer)changeData.get(ENTITY_VERSION_KEY);
            Map<String,Object> patch = (Map<String,Object>)changeData.get(VALUE_KEY);
            String changeVersion = (String)changeData.get(CHANGE_VERSION_KEY);

            return new RemoteChange(client_id, id, ccids, changeVersion, sourceVersion, objectVersion, operation, patch);

        }

    }
    /**
     * ChangeProcessor should perform operations on a seperate thread as to not block the websocket
     * ideally it will be a FIFO queue processor so as changes are brought in they can be appended.
     * We also need a way to pause and clear the queue when we download a new index.
     */
    private class ChangeProcessor implements Runnable, OnChangeRetryListener {

        // public static final Integer CAPACITY = 200;


        public static final long RETRY_DELAY_MS       = 5000; // 5 seconds for retries?
        public static final String PENDING_KEY = "pending";
        public static final String QUEUED_KEY = "queued";

        private Bucket<? extends Bucket.Syncable> bucket;
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

        public ChangeProcessor(Bucket<? extends Bucket.Syncable> bucket, ChangeProcessorListener listener) {
            this.bucket = bucket;
            this.listener = listener;
            this.remoteQueue = new ArrayList<Map<String,Object>>();
            this.localQueue = new ArrayList<Change>();
            this.pendingChanges = new HashMap<String,Change>();
            this.handler = new Handler();
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
                Simperium.log(String.format("Could not serialize change processor queue to file %s", getFileName()), e);
            }
        }
        /**
         * Save state of pending and locally queued items
         */
        private void saveToFile() throws java.io.IOException {
            //  construct JSON string of pending and local queue
            Simperium.log(String.format("Saving to file %s", getFileName()));
            synchronized(lock){
                FileOutputStream stream = null;
                try {
                    stream = context.openFileOutput(getFileName(), Context.MODE_PRIVATE);
                    Map<String,Object> serialized = new HashMap<String,Object>(2);
                    serialized.put(PENDING_KEY, pendingChanges);
                    serialized.put(QUEUED_KEY, localQueue);
                    JSONObject json = Channel.serializeJSON(serialized);
                    String jsonString = json.toString();
                    Simperium.log(String.format("Saving: %s", jsonString));
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
                Simperium.log(String.format("Could not restore from file: %s", getFileName()), e);
            }
            Simperium.log(
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
                    Simperium.log(String.format("We have changes from serialized file %s", changeData));
                    
                    if (changeData.containsKey(PENDING_KEY)) {
                        Map<String,Map<String,Object>> pendingData = (Map<String,Map<String,Object>>)changeData.get(PENDING_KEY);
                        Iterator<Map.Entry<String,Map<String,Object>>> pendingEntries = pendingData.entrySet().iterator();
                        while(pendingEntries.hasNext()){
                            Map.Entry<String, Map<String,Object>> entry = pendingEntries.next();
                            pendingChanges.put(entry.getKey(), Change.buildChange(entry.getValue()));
                        }
                    }
                    if (changeData.containsKey(QUEUED_KEY)) {
                        List<Map<String,Object>> queuedData = (List<Map<String,Object>>)changeData.get(QUEUED_KEY);
                        Iterator<Map<String,Object>> queuedItems = queuedData.iterator();
                        while(queuedItems.hasNext()){
                            localQueue.add(Change.buildChange(queuedItems.next()));
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
            if (thread == null || thread.getState() == Thread.State.TERMINATED) {
                Simperium.log("Starting up the change processor");
                thread = new Thread(this);
                thread.start();
            }
        }

        public void stop(){
            // interrupt the thread
            if (this.thread != null) {
                this.thread.interrupt();
            }
        }

        protected void clear(){
            this.pendingChanges.clear();
            this.remoteQueue.clear();
            this.localQueue.clear();
        }

        protected void abort(){
            clear();
            stop();
        }


        public void run(){
            // apply any remote changes we have
            synchronized(lock){
                // bail if thread is interrupted
                while(remoteQueue.size() > 0 && !Thread.interrupted()){
                    // take an item off the queue
                    RemoteChange remoteChange = RemoteChange.buildFromMap(remoteQueue.remove(0));
                    Simperium.log(String.format("Received remote change with cv: %s", remoteChange.getChangeVersion()));
                    Boolean acknowledged = false;
                    // synchronizing on pendingChanges since we're looking up and potentially
                    // removing an entry
                    Change change = pendingChanges.get(remoteChange.getKey());
                    if (change != null && remoteChange.isAcknowledgedBy(change)) {
                        // this is an ACK
                        // stop the change from sending retries
                        change.stopRetryTimer();
                        // change is no longer pending so remove it
                        pendingChanges.remove(change.getKey());
                        if (remoteChange.isError()) {
                            Simperium.log(String.format("Change error response! %d %s", remoteChange.getErrorCode(), remoteChange.getKey()));
                            // TODO: determine if we can retry this change by reapplying
                        } else {
                            acknowledged = true;
                        }
                    }
                    // apply the remote change
                    applyRemoteChange(remoteChange, acknowledged);
                }
            }
            // if we have a change to an object that is waiting for an ack
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
                        Simperium.log(String.format("Changes pending for %s re-queueing %s", localChange.getKey(), localChange.getChangeId()));
                        sendLater.add(localChange);
                        // let's get the next change
                    } else {
                        // send the change to simperium, if the change ends up being empty
                        // then we'll just skip it
                        if(sendChange(localChange)) {
                            // add the change to pending changes
                            pendingChanges.put(localChange.getKey(), localChange);
                            localChange.setOnChangeRetryListener(this);
                            // starts up the timer
                            this.retryTimer.scheduleAtFixedRate(localChange, RETRY_DELAY_MS, RETRY_DELAY_MS);
                        }
                    }
                }
            }
            // add the sendLater changes back on top of the queue
            synchronized(lock){
                localQueue.addAll(0, sendLater);
                save();
                Simperium.log(
                    String.format(
                        "Done processing thread with %d remote and %d local changes %d pending",
                        remoteQueue.size(), localQueue.size(), pendingChanges.size()
                    )
                );
            }
        }
        
        public void onRetryChange(Change change){
            sendChange(change);
        }
        
        private Boolean sendChange(Change change){
            // send the change down the socket!
            if (!started) {
                // channel is not initialized, send on reconnect
                Simperium.log(String.format("Abort sending change, channel not initialized: %s", change.getChangeId()));
                return true;
            }
            Simperium.log(String.format("Send local change: %s", change.getChangeId()));

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

        private void applyRemoteChange(final RemoteChange change, final Boolean acknowledged){
            final ChangeProcessorListener changeListener = listener;
            if (change == null) return;
            //TODO: error responses that can be retried should be retired here?
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
                    }
                });
                return;
            }
            Bucket.Ghost object;
            if (change.isAddOperation()) {
                object = new Bucket.Ghost(change.getKey(), 0, new HashMap<String,Object>());
            } else {
                object = bucket.getGhost(change.getKey());
                if (null == object) {
                   Simperium.log(String.format("Local object missing: %s", change.getKey()));
                   return;
                }
                // we need to check if we have the correct version
                // TODO: handle how to sync if source version doesn't match local object
                if (!change.getSourceVersion().equals(object.getVersion())) {
                    Simperium.log(String.format("Local version %d of object does not match sv: %s %d", object.getVersion(), change.getKey(), change.getSourceVersion()));
                    return;
                }
            }

            // construct the new object
            final Bucket.Ghost updated = new Bucket.Ghost(
                change.getKey(),
                change.getObjectVersion(),
                jsondiff.apply(object.getDiffableValue(), change.getPatch())
            );
            // Compress all queued changes for the same object into a single change
            synchronized (lock){
                Change compressed = null;
                Iterator<Change> queuedChanges = localQueue.iterator();
                while(queuedChanges.hasNext()){
                    Change queuedChange = queuedChanges.next();
                    if (queuedChange.getKey().equals(change.getKey())) {
                        queuedChanges.remove();
                        Simperium.log(String.format("Compressed queued local change for %s", queuedChange.getKey()));
                        compressed = queuedChange.reapplyOrigin(updated.getVersion(), updated.getDiffableValue());
                    }
                }
                if (compressed != null) {
                    localQueue.add(compressed);
                }
            }
            
            // notify the listener
            final boolean isNew = change.isAddOperation();
			// if it's an acknowledge then we don't need to update the storageprovider
			// but we want to update the stored ghost object
            this.handler.post(new Runnable(){
                public void run(){
					if (acknowledged) {
						changeListener.onAcknowledgedChange(change.getChangeVersion(), change.getKey(), updated);
					} else if(isNew){
                        changeListener.onAddObject(change.getChangeVersion(), change.getKey(), updated);
                    } else {
                        changeListener.onUpdateObject(change.getChangeVersion(), change.getKey(), updated);
                    }
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
                // log(String.format("Hello! %s", json.get(key).getClass().getName()));
                if (val.getClass().equals(JSONObject.class)) {
                    map.put(key, convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    map.put(key, convertJSON((JSONArray) val));
                } else {
                    map.put(key, val);
                }
            } catch (JSONException e) {
                Simperium.log(String.format("Failed to convert JSON: %s", e.getMessage()), e);
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
                Simperium.log(String.format("Faile to convert JSON: %s", e.getMessage()), e);
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
            } else if(val instanceof Change){
                json.put(serializeJSON(((Change) val).toJSONSerializable()));
            } else {
                json.put(val);
            }
        }
        return json;
    }

}
