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
 */
package com.simperium.client;

import com.simperium.SimperiumException;
import com.simperium.Version;
import com.simperium.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.Executor;


public class Channel implements Bucket.Channel {

    public static class ChangeNotSentException extends ChangeException {

        public ChangeNotSentException(Change change, String message) {
            super(change, message);
        }

        public ChangeNotSentException(Change change, Throwable cause) {
            super(change, cause);
        }

    }

    public interface OnMessageListener {
        void onMessage(MessageEvent event);
        void onLog(Channel channel, int level, CharSequence message);
        void onClose(Channel channel);
        void onOpen(Channel channel);
    }

    public static final String TAG="Simperium.Channel";
    // key names for init command json object
    static public final String FIELD_CLIENT_ID       = "clientid";
    static public final String FIELD_API_VERSION     = "api";
    static public final String FIELD_AUTH_TOKEN      = "token";
    static public final String FIELD_APP_ID          = "app_id";
    static public final String FIELD_BUCKET_NAME     = "name";
    static public final String FIELD_COMMAND         = "cmd";
    static public final String FIELD_LIBRARY         = "library";
    static public final String FIELD_LIBRARY_VERSION = "version";

    static public final String SIMPERIUM_API_VERSION = "1.1";
    static public final String LIBRARY_NAME = "android";
    static public final Integer LIBRARY_VERSION = 0;
    static public final Integer RETRY_LIMIT = 1;

    // commands sent over the socket
    public static final String COMMAND_INIT      = "init"; // init:{INIT_PROPERTIES}
    public static final String COMMAND_AUTH      = "auth"; // received after an init: auth:expired or auth:email@example.com
    public static final String COMMAND_INDEX     = "i"; // i:1:MARK:?:LIMIT
    public static final String COMMAND_CHANGE    = "c";
    public static final String COMMAND_VERSION   = "cv";
    public static final String COMMAND_ENTITY    = "e";
    static public final String COMMAND_INDEX_STATE = "index";

    static final String RESPONSE_UNKNOWN  = "?";

    static final String EXPIRED_AUTH      = "expired"; // after unsuccessful init:
    static final String EXPIRED_AUTH_INDICATOR = "{";
    static final String EXPIRED_AUTH_REASON_KEY = "msg";
    static final String EXPIRED_AUTH_CODE_KEY = "code";
    static final int EXPIRED_AUTH_INVALID_TOKEN_CODE = 401;

    static public final int LOG_DEBUG = ChannelProvider.LOG_DEBUG;

    // Parameters for querying bucket
    static final Integer INDEX_PAGE_SIZE  = 50;
    static final Integer INDEX_BATCH_SIZE = 10;
    static final Integer INDEX_QUEUE_SIZE = 5;

    // Constants for parsing command messages
    static final Integer MESSAGE_PARTS = 2;
    static final Integer COMMAND_PART  = 0;
    static final Integer PAYLOAD_PART  = 1;
    static final String  COMMAND_FORMAT = "%s:%s";

    // bucket determines which bucket we are using on this channel
    private Bucket bucket;
    // the object the receives the messages the channel emits
    private OnMessageListener listener;
    // track channel status
    protected boolean started = false, connected = false, startOnConnect = false, idle = true;
    private boolean haveIndex = false;
    private CommandInvoker commands = new CommandInvoker();
    private String appId, sessionId;
    private Serializer serializer;
    protected Executor mExecutor;

    // for sending and receiving changes
    final private ChangeProcessor changeProcessor;
    private IndexProcessor indexProcessor;
    
    public interface Serializer {
        // public <T extends Syncable> void save(Bucket<T> bucket, SerializedQueue<T> data);
        public SerializedQueue restore(Bucket bucket);
        public void reset(Bucket bucket);
        public void onQueueChange(Change change);
        public void onDequeueChange(Change change);
        public void onSendChange(Change change);
        public void onAcknowledgeChange(Change change);
    }

    public static class SerializedQueue {
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

    public Channel(Executor executor, String appId, String sessionId, final Bucket bucket, Serializer serializer, OnMessageListener listener){
        mExecutor = executor;
        this.serializer = serializer;
        this.appId = appId;
        this.sessionId = sessionId;
        this.bucket = bucket;
        this.listener = listener;
        // Receive auth: command
        command(COMMAND_AUTH, new Command(){
            public void execute(String param){
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

            @Override
            public void execute(String param){
                updateIndex(param);
            }

        });

        // Receive c: command
        command(COMMAND_CHANGE, new Command(){

            @Override
            public void execute(String param){
                handleRemoteChanges(param);
            }

        });

        // Receive e: command
        command(COMMAND_ENTITY, new Command(){

            @Override
            public void execute(String param){
                handleVersionResponse(param);
            }

        });

        // Receive index command
        command(COMMAND_INDEX_STATE, new Command() {

            @Override
            public void execute(String param){
                sendIndexStatus();
            }

        });

        // Receive cv:? command
        command(COMMAND_VERSION, new Command() {

            @Override
            public void execute(String param) {

                if (param.equals(RESPONSE_UNKNOWN)) {
                    Logger.log(TAG, "CV is out of date");
                    stopChangesAndRequestIndex();
                }

            }

        });

        changeProcessor = new ChangeProcessor();
    }

    public String toString(){
        return String.format("%s<%s>", super.toString(), bucket.getName());
    }

    protected Ghost onAcknowledged(RemoteChange remoteChange, Change acknowledgedChange)
    throws RemoteChangeInvalidException {
        // if this isn't a removal, update the ghost for the relevant object
        return bucket.acknowledgeChange(remoteChange, acknowledgedChange);
    }

    /**
     * Handle errors from simperium service
     * see <a href="https://gist.github.com/beaucollins/6998802#error-responses">Error Responses</a>
     */
    protected void onError(RemoteChange remoteChange, Change erroredChange){
        RemoteChange.ResponseCode errorCode = remoteChange.getResponseCode();
        switch (errorCode) {
            case INVALID_VERSION:
                // Bad version, client referencing a wrong or missing sv. This is a potential data
                // loss scenario: server may not have enough history to resolve conflicts. Client
                // has two options:
                //
                // - re-load the entity (via e command) then overwrite the local changes
                // - send a change with full object (which will overwrite remote changes, history
                //   will still be available) referencing the current sv
                requeueChangeWithFullObject(erroredChange);
                break;
            case INVALID_DIFF:
                // Server could not apply diff, resend the change with additional parameter d that
                // contains the whole JSON data object. Current known scenarios where this could
                // happen:
                //
                // - Client generated an invalid diff (some old versions of iOS library)
                // - Client is sending string diffs and using a different encoding than server
                requeueChangeWithFullObject(erroredChange);
                break;
            case UNAUTHORIZED:
                // Grab a new authentication token (or possible you just don't
                // have access to that document).

                // TODO: update unauthorized state for User
                break;
            case NOT_FOUND:
                // Client is referencing an object that does not exist on server.
                // If client is insistent, then changing the diff such that it is creating the
                // object instead should make this succeed.
                // TODO: Allow clients to handle NOT_FOUND
                break;
            case INVALID_ID:
                // If it was an invalid id, changing the id could make the call succeed. If it was a
                // schema violation, then correction will depend on the schema. If client cannot
                // tell, then do not re-send since unless something changes, 400 will be sent every
                // time.
                // TODO: Allow client implemenations to handle INVALID_ID
            case EXCEEDS_MAX_SIZE:
                // Nothing to do except reduce the size of the object
                // TODO: Allow client implementations to handle EXCEEDS_MAX_SIZE
            case DUPLICATE_CHANGE:
                // Duplicate change, client can safely throw away the change it is attempting to send
            case EMPTY_CHANGE:
                // Empty change, nothing was changed on the server, client can ignore (and stop
                // sending change).
            case OK:
                // noop
                break;
        }
        Logger.log(TAG, String.format("Received error from service %s", remoteChange));
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
    public Change queueLocalChange(Syncable object){
        Change change = new Change(Change.OPERATION_MODIFY, object);
        changeProcessor.addChange(change);
        return change;
    }

    public Change queueLocalDeletion(Syncable object){
        Change change = new Change(Change.OPERATION_REMOVE, object);
        changeProcessor.addChange(change);
        return change;
    }

    protected void requeueChangeWithFullObject(Change change) {
        // Don't requeue this change if we've retried past the allowed limit.
        if (change.getRetryCount() >= RETRY_LIMIT) {
            completeAndDequeueChange(change);
            return;
        }

        change.incrementRetryCount();
        change.setSendFullObject(true);
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
            // noop, api 1.1 should not be sending ? here
            return;
        }
        JSONObject index;
        try {
            index = new JSONObject(indexJson);
        } catch (JSONException e) {
            Logger.log(TAG, String.format("Index had invalid json: %s", indexJson));
            return;
        }
        // if we don't have a processor or we are getting a different cv
        if (indexProcessor == null || !indexProcessor.addIndexPage(index)) {
            // make sure we're not processing changes and clear pending changes
            // TODO: pause the change processor instead of clearing it :(
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
            indexProcessor.start(index);
        } else {
            // received an index page for a different change version
            // TODO: reconcile the out of band index cv
        }

    }

    private IndexProcessorListener indexProcessorListener = new IndexProcessorListener(){
        @Override
        public void onComplete(String cv){
            haveIndex = true;
        }
    };

    private void handleRemoteChanges(String changesJson){
        JSONArray changes;
        if (changesJson.equals(RESPONSE_UNKNOWN)) {
            // noop API 1.1 does not send "?" here
            return;
        }
        try {
            changes = new JSONArray(changesJson);
        } catch (JSONException e){
            Logger.log(TAG, "Failed to parse remote changes JSON", e);
            return;
        }
        // Loop through each change? Convert changes to array list
        changeProcessor.addChanges(changes);
    }

    private static final String ENTITY_DATA_KEY = "data";
    private void handleVersionResponse(String versionData){

        try {
            // versionData will be: key.version\n{"data":ENTITY}
            ObjectVersionData objectVersion = ObjectVersionData.parseString(versionData);

            if (indexProcessor == null)
                throw new ObjectVersionUnexpectedException(objectVersion);

            indexProcessor.addObjectData(objectVersion);

        } catch (ObjectVersionUnexpectedException e) {

            ObjectVersionData data = e.versionData;

            Ghost ghost = new Ghost(data.getKey(), data.getVersion(),
                data.getData());

            bucket.updateGhost(ghost, null);

        } catch (ObjectVersionUnknownException e) {
            log(LOG_DEBUG, String.format(Locale.US, "Object version does not exist %s", e.version));
        } catch (ObjectVersionDataInvalidException e) {
            log(LOG_DEBUG, String.format(Locale.US, "Object version JSON data malformed %s", e.version));
        } catch (ObjectVersionParseException e) {
            log(LOG_DEBUG, String.format(Locale.US, "Received invalid object version: %s", e.versionString));
        }


    }

    /**
     * Stop sending changes and download a new index
     */
    private void stopChangesAndRequestIndex() {

        // get the latest index
        getLatestVersions();

    }

    /**
     * Send index status JSON
     */
    private void sendIndexStatus() {
        mExecutor.execute(new Runnable(){

            @Override
            public void run(){

                int total = bucket.count();
                JSONArray objectVersions = new JSONArray();
                String idKey = "id";
                String versionKey = "v";
                Bucket.ObjectCursor objects = bucket.allObjects();

                // collect all object keys and versions
                while(objects.moveToNext()) {
                    try {
                        JSONObject objectData = new JSONObject();
                        Syncable object = objects.getObject();
                        objectData.put(idKey, object.getSimperiumKey());
                        objectData.put(versionKey, object.getVersion());
                        objectVersions.put(objectData);
                    } catch (JSONException e) {
                        Logger.log(TAG, "Unable to add object version", e);
                    }
                }

                // collect all pending change keys, ccids and source versions
                Collection<Change> pending = changeProcessor.pendingChanges();
                JSONArray pendingData = new JSONArray();
                for (Change change : pending) {
                    try {
                        JSONObject changeData = new JSONObject();
                        changeData.put("id", change.getKey());
                        changeData.put("sv", change.getVersion());
                        changeData.put("ccid", change.getChangeId());
                        pendingData.put(changeData);
                    } catch (JSONException e) {
                        Logger.log(TAG, "Unable to add change", e);
                    }
                }

                // set up the index information
                JSONObject index = new JSONObject();
                try {
                    index.put("index", objectVersions);
                    index.put("current", getChangeVersion());
                    index.put("pending", pendingData);
                } catch (JSONException e) {
                    Logger.log(TAG, "Unable to build index response", e);
                }

                // add extra debugging info
                JSONObject extra = new JSONObject();
                try {
                    extra.put("bucketName", bucket.getName());
                    extra.put("build", Version.BUILD);
                    extra.put("version", Version.NUMBER);
                    extra.put("client", Version.NAME);

                    index.put("extra", extra);
                } catch (JSONException e) {
                    Logger.log(TAG, "Unable to add extra info", e);
                }


                sendMessage(String.format("%s:%s", COMMAND_INDEX_STATE, index));

            }

        });
    }

    public Bucket getBucket(){
        return bucket;
    }

    public String getBucketName(){
        if (bucket != null) {
            return bucket.getName();
        }
        return "";
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
     * ChangeProcessor has no work to do
     */
    public boolean isIdle(){
        return idle;
    }

    public void log(int level, CharSequence message) {
        if (this.listener != null) {
            this.listener.onLog(this, level, message);
        }
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
        init.put(FIELD_API_VERSION, SIMPERIUM_API_VERSION);
        init.put(FIELD_CLIENT_ID, sessionId);
        init.put(FIELD_APP_ID, appId);
        init.put(FIELD_AUTH_TOKEN, bucket.getUser().getAccessToken());
        init.put(FIELD_BUCKET_NAME, bucket.getRemoteName());
        init.put(FIELD_COMMAND, initialCommand.toString());
        init.put(FIELD_LIBRARY_VERSION, LIBRARY_VERSION);
        init.put(FIELD_LIBRARY, LIBRARY_NAME);
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
        if (listener != null) {
            listener.onClose(this);
        }
    }

    // websocket
    public void onConnect(){
        connected = true;
        Logger.log(TAG, String.format("onConnect autoStart? %b", startOnConnect));
        if(startOnConnect) start();
    }

    public void onDisconnect(){
        started = false;
        connected = false;
    }
    /**
     * Receive a message from the WebSocketManager which already strips the channel
     * prefix from the message.
     */
    public void receiveMessage(String message){
        // parse the message and react to it
        String[] parts = message.split(":", MESSAGE_PARTS);
        String command = parts[COMMAND_PART];

        if (parts.length == 2) {
            executeCommand(command, parts[1]);
        } else if (parts.length == 1) {
            executeCommand(command, "");
        }

    }

    // send without the channel id, the socket manager should know which channel is writing
    private void sendMessage(String message){
        // send a message
        if (listener != null) {
            MessageEvent event = new MessageEvent(this, message);
            listener.onMessage(event);
        }
    }

    public static class MessageEvent extends EventObject {

        public final String message;
        public final Channel channel;

        public MessageEvent(Channel source, String message) {
            super(source);
            this.message = message;
            this.channel = source;
        }

        public String getMessage() {
            return message;
        }

        public String toString() {
            return getMessage();
        }

        public Channel getChannel() {
            return this.channel;
        }

    }

    private void command(String name, Command command){
        commands.add(name, command);
    }

    private void executeCommand(String name, String params){
        commands.executeCommand(name, params);
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
        void execute(String params);
    }

    private class CommandInvoker {
        private HashMap<String,Command> commands = new HashMap<String,Command>();

        protected CommandInvoker add(String name, Command command){
            commands.put(name, command);
            return this;
        }

        protected void executeCommand(String name, String params){
            if (commands.containsKey(name)) {
                Command command = commands.get(name);
                command.execute(params);
            } else {
                Logger.log(TAG, String.format("Unkown command received: %s", name));
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

        public IndexQuery(){}

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

    public static class ObjectVersion {

        public final String key;
        public final Integer version;

        public ObjectVersion(String key, Integer version){
            this.key = key;
            this.version = version;
        }

        public String getKey(){
            return key;
        }

        public Integer getVersion(){
            return version;
        }

        public String toString(){
            return String.format(Locale.US, "%s.%d", key, version);
        }

        public static ObjectVersion parseString(String versionString)
        throws ObjectVersionParseException {
            int lastDot = versionString.lastIndexOf(".");
            if (lastDot == -1) {
                throw new ObjectVersionParseException(versionString);
            }
            String key = versionString.substring(0, lastDot);
            String version = versionString.substring(lastDot + 1);
            return new ObjectVersion(key, Integer.parseInt(version));
        }
    }

    public static class ObjectVersionData {

        public final ObjectVersion version;
        public final JSONObject data;

        public ObjectVersionData(ObjectVersion version, JSONObject data) {
            this.version = version;
            this.data = data;
        }

        public String toString() {
            return this.version.toString();
        }

        public String getKey() {
            return this.version.key;
        }

        public Integer getVersion() {
            return this.version.version;
        }

        public JSONObject getData(){
            return this.data;
        }

        public static ObjectVersionData parseString(String versionString)
        throws ObjectVersionParseException, ObjectVersionUnknownException,
        ObjectVersionDataInvalidException {

            String[] objectParts = versionString.split("\n");
            String prefix = objectParts[0];
            String payload = objectParts[1];

            ObjectVersion objectVersion;

            objectVersion = ObjectVersion.parseString(prefix);

            if (payload.equals(RESPONSE_UNKNOWN)) {
                throw new ObjectVersionUnknownException(objectVersion);
            }

            try {
                JSONObject data = new JSONObject(payload);
                return new ObjectVersionData(objectVersion, data.getJSONObject(ENTITY_DATA_KEY));
            } catch (JSONException e) {
                throw new ObjectVersionDataInvalidException(objectVersion, e);
            }

        }

    }

    /**
     * Thrown when e:id.v\n? received
     */
    public static class ObjectVersionUnknownException extends SimperiumException {

        public final ObjectVersion version;

        public ObjectVersionUnknownException(ObjectVersion version) {
            super();
            this.version = version;
        }

    }

    /**
     * Unable to parse the object key and version number from e:key.v
     */
    public static class ObjectVersionParseException extends SimperiumException {

        public final String versionString;

        public ObjectVersionParseException(String versionString) {
            super();
            this.versionString = versionString;
        }

    }

    /**
     * Invalid data received for e:key.v\nJSON
     */
    public static class ObjectVersionDataInvalidException extends SimperiumException {

        public final ObjectVersion version;
        public ObjectVersionDataInvalidException(ObjectVersion version, Throwable cause) {
            super(cause);
            this.version = version;
        }

    }

    public static class ObjectVersionUnexpectedException extends SimperiumException {
        public final ObjectVersionData versionData;

        public ObjectVersionUnexpectedException(ObjectVersionData versionData) {
            super();
            this.versionData = versionData;
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
        private List<String> queue = Collections.synchronizedList(new ArrayList<String>());
        private IndexQuery nextQuery;
        private boolean complete = false;
        final private IndexProcessorListener listener;
        int indexedCount = 0;

        public IndexProcessor(Bucket bucket, String cv, IndexProcessorListener listener){
            this.bucket = bucket;
            this.cv = cv;
            this.listener = listener;
        }

        /**
         * Receive an object's version data and store it. Send the request for the
         * next object.
         */
        public void addObjectData(ObjectVersionData objectVersion)
        throws ObjectVersionUnexpectedException {

            if (!queue.remove(objectVersion.toString()))
                throw new ObjectVersionUnexpectedException(objectVersion);

            // build the ghost and update
            Ghost ghost = new Ghost(objectVersion.getKey(), objectVersion.getVersion(), objectVersion.getData());
            bucket.addObjectWithGhost(ghost);
            indexedCount ++;

            // for every 10 items, notify progress
            if(indexedCount % 10 == 0) {
                notifyProgress();
            }
            next();
        }

        public void start(JSONObject indexPage){
            addIndexPage(indexPage);
        }

        public void next(){

            // if queue isn't empty, pull it off the top and send request
            if (!queue.isEmpty()) {
                String versionString = queue.get(0);
                ObjectVersion version;
                try {
                    version = ObjectVersion.parseString(versionString);
                } catch (ObjectVersionParseException e) {
                    Logger.log(TAG, "Failed to parse version string, skipping", e);
                    queue.remove(versionString);
                    next();
                    return;
                }

                if (!bucket.hasKeyVersion(version.getKey(), version.getVersion())) {
                    sendMessage(String.format("%s:%s", COMMAND_ENTITY, version.toString()));
                } else {
                    Logger.log(TAG, String.format("Already have %s requesting next object", version));
                    queue.remove(versionString);
                    next();
                    return;
                }
                return;
            }

            // if index is empty and we have a next request, make the request
            if (nextQuery != null){
                sendMessage(nextQuery.toString());
                return;
            }

            // no queue, no next query, all done!
            complete = true;
            notifyDone();

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

            if (indexVersions.length() > 0) {
                // query for each item that we don't have locally in the bucket
                for (int i=0; i<indexVersions.length(); i++) {
                    try {

                        JSONObject version = indexVersions.getJSONObject(i);
                        String key  = version.getString(INDEX_OBJECT_ID_KEY);
                        Integer versionNumber = version.getInt(INDEX_OBJECT_VERSION_KEY);
                        ObjectVersion objectVersion = new ObjectVersion(key, versionNumber);
                        queue.add(objectVersion.toString());
                        // if (!bucket.hasKeyVersion(key, versionNumber)) {
                        //     // we need to get the remote object
                        //     index.add(objectVersion.toString());
                        //     // sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, versionNumber));
                        // }

                    } catch (JSONException e) {
                        Logger.log(TAG, String.format("Error processing index: %d", i), e);
                    }

                }

            }

            String nextMark = null;
            if (indexPage.has(INDEX_MARK_KEY)) {
                try {
                    nextMark = indexPage.getString(INDEX_MARK_KEY);
                } catch (JSONException e) {
                    nextMark = null;
                }
            }

            if (nextMark != null && nextMark.length() > 0) {
                nextQuery = new IndexQuery(nextMark);
                // sendMessage(nextQuery.toString());
            } else {
                nextQuery = null;
            }
            next();
            return true;
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

    public boolean haveCompleteIndex(){
        return haveIndex;
    }

    /**
     * ChangeProcessor should perform operations on a seperate thread as to not block the websocket
     * ideally it will be a FIFO queue processor so as changes are brought in they can be appended.
     * We also need a way to pause and clear the queue when we download a new index.
     */
    private class ChangeProcessor implements Runnable, Change.OnRetryListener {

        // wait 5 seconds for retries
        public static final long RETRY_DELAY_MS = 5000; 
        private List<JSONObject> remoteQueue = Collections.synchronizedList(new ArrayList<JSONObject>(10));
        private List<Change> localQueue = Collections.synchronizedList(new ArrayList<Change>());
        private Map<String,Change> pendingChanges = Collections.synchronizedMap(new HashMap<String,Change>());
        private Timer mRetryTimer;
        private Thread mThread;
        private final Object mLock = new Object();
        private final Object mRunLock = new Object();

        public ChangeProcessor() {
            restore();
        }

        public Collection<Change> pendingChanges() {
            return pendingChanges.values();
        }

        private void restore(){
            synchronized(mLock){
                SerializedQueue serialized = serializer.restore(bucket);
                localQueue.addAll(serialized.queued);
                pendingChanges.putAll(serialized.pending);
                resendPendingChanges();
            }
        }

        public void addChanges(JSONArray changes) {
            synchronized(mLock){
                int length = changes.length();
                Logger.log(TAG, String.format("Add remote changes to processor %d", length));
                log(LOG_DEBUG, String.format(Locale.US, "Adding %d remote changes to queue", length));
                for (int i = 0; i < length; i++) {
                    JSONObject change = changes.optJSONObject(i);
                    if (change != null) {
                        remoteQueue.add(change);
                    }
                }
                start();
            }
        }

        /**
         * Local change to be queued
         */
        public void addChange(Change change){
            synchronized(mLock) {
                // compress all changes for this same key
                log(LOG_DEBUG, String.format(Locale.US, "Adding new change to queue %s.%d %s %s",
                    change.getKey(), change.getVersion(), change.getOperation(), change.getChangeId()));

                Iterator<Change> iterator = localQueue.iterator();
                boolean isModify = change.isModifyOperation();
                while(iterator.hasNext() && isModify){
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
            if (!started) {
                return;
            }
            if (mRetryTimer == null) {
                mRetryTimer = new Timer();
            }
            if (mThread == null || mThread.getState() == Thread.State.TERMINATED) {
                mThread = new Thread(this, String.format("simperium.processor.%s", getBucket().getName()));
                mThread.start();
            } else {
                // notify
                synchronized(mRunLock) {
                    mRunLock.notify();
                }
            }
        }

        public void stop(){
            // interrupt the thread
            if (mThread != null) {
                mThread.interrupt();
                synchronized(mRunLock) {
                    mRunLock.notify();
                }
            }
        }

        protected void reset(){
            pendingChanges.clear();
            serializer.reset(bucket);
        }

        protected void abort(){
            reset();
        }

        /**
         * Check if we have changes we can send out
         */
        protected boolean hasQueuedChanges(){
            synchronized(mLock) {
                Logger.log(TAG, String.format("Checking for queued changes %d", localQueue.size()));
                // if we have have any remote changes to process we have work to do
                if (!remoteQueue.isEmpty()) return true;
                // if our local queue is empty we don't have work to do
                if (localQueue.isEmpty()) return false;
                // if we have queued changes, if there's no corresponding pending change then there's still work to do
                Iterator<Change> changes = localQueue.iterator();
                while(changes.hasNext()){
                    Change change = changes.next();
                    if (!pendingChanges.containsKey(change.getKey())) return true;
                }
                return false;
            }
        }

        protected boolean hasPendingChanges(){
            synchronized(mLock) {
                return !pendingChanges.isEmpty() || !localQueue.isEmpty();
            }
        }

        public void run(){
            if(!haveCompleteIndex()) return;
            idle = false;
            Logger.log(TAG, String.format("%s - Starting change queue", Thread.currentThread().getName()));
            while(true){
                try {
                    processRemoteChanges();
                    processLocalChanges();
                } catch (InterruptedException e) {
                    // shut down
                    break;
                }
                if(!hasQueuedChanges()){
                    // we've sent out every change that we can so far, if nothing is pending we can disconnect
                    if (pendingChanges.isEmpty()) {
                        idle = true;
                    }

                    synchronized(mRunLock) {
                        try {
                            Logger.log(TAG, String.format("Waiting <%s> idle? %b", bucket.getName(), idle));
                            log(LOG_DEBUG, "Change queue is empty, waiting for changes");
                            mRunLock.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                        Logger.log(TAG, "Waking change processor");
                        log(LOG_DEBUG, "Processing changes");
                    }
                }
            }
            mRetryTimer.cancel();
            mRetryTimer = null;
            Logger.log(TAG, String.format("%s - Queue interrupted", Thread.currentThread().getName()));
        }


        private void processRemoteChanges()
        throws InterruptedException {
            synchronized(mLock) {
                Logger.log(TAG, String.format("Processing remote changes %d", remoteQueue.size()));
                // bail if thread is interrupted
                while(remoteQueue.size() > 0){
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    // take an item off the queue
                    RemoteChange remoteChange = null;
                    try {
                        remoteChange = RemoteChange.buildFromMap(remoteQueue.remove(0));
                    } catch (JSONException e) {
                        Logger.log(TAG, "Failed to build remote change", e);
                        continue;
                    }
                    log(LOG_DEBUG, String.format("Processing remote change with cv: %s", remoteChange.getChangeVersion()));
                    Boolean acknowledged = false;
                    // synchronizing on pendingChanges since we're looking up and potentially
                    // removing an entry
                    Change change = null;
                    change = pendingChanges.get(remoteChange.getKey());
                    if (remoteChange.isAcknowledgedBy(change)) {
                        log(LOG_DEBUG, String.format("Found pending change for remote change <%s>: %s", remoteChange.getChangeVersion(), change.getChangeId()));
                        serializer.onAcknowledgeChange(change);
                        // change is no longer pending so remove it
                        pendingChanges.remove(change.getKey());
                        if (remoteChange.isError()) {
                            Logger.log(TAG, String.format("Change error response! %d %s", remoteChange.getErrorCode(), remoteChange.getKey()));
                            onError(remoteChange, change);
                        } else {
                            try {
                                Ghost ghost = onAcknowledged(remoteChange, change);
                                Change compressed = null;
                                Iterator<Change> queuedChanges = localQueue.iterator();
                                while(queuedChanges.hasNext()){
                                    Change queuedChange = queuedChanges.next();
                                    if (queuedChange.getKey().equals(change.getKey())) {
                                        queuedChanges.remove();
                                        if (!remoteChange.isRemoveOperation()) {
                                            compressed = queuedChange.reapplyOrigin(ghost.getVersion(), ghost.getDiffableValue());
                                        }
                                    }
                                }
                                if (compressed != null) {
                                    localQueue.add(compressed);
                                }
                            } catch (RemoteChangeInvalidException e){
                                Logger.log(TAG, "Remote change could not be acknowledged", e);
                                log(LOG_DEBUG, String.format("Failed to acknowledge change <%s> Reason: %s", remoteChange.getChangeVersion(), e.getMessage()));
                            }
                        }
                    } else {
                        if (remoteChange.isError()){
                            Logger.log(TAG, String.format("Remote change %s was an error but not acknowledged", remoteChange));
                            log(LOG_DEBUG, String.format("Received error response for change but not waiting for any ccids <%s>", remoteChange.getChangeVersion()));
                        } else {
                            try {
                                bucket.applyRemoteChange(remoteChange);
                                Logger.log(TAG, String.format("Succesfully applied remote change <%s>", remoteChange.getChangeVersion()));
                            } catch (RemoteChangeInvalidException e) {
                                Logger.log(TAG, "Remote change could not be applied", e);
                                log(LOG_DEBUG, String.format("Failed to apply change <%s> Reason: %s", remoteChange.getChangeVersion(), e.getMessage()));
                                // request the full object for the new version
                                ObjectVersion version = new ObjectVersion(remoteChange.getKey(), remoteChange.getObjectVersion());
                                sendMessage(String.format("%s:%s", COMMAND_ENTITY, version));
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

        public void processLocalChanges()
        throws InterruptedException {
            synchronized(mLock) {
                if (localQueue.isEmpty()) {
                    return;
                }
                final List<Change> sendLater = new ArrayList<Change>();
                // find the first local change whose key does not exist in the pendingChanges and there are no remote changes
                while(!localQueue.isEmpty()){
                    if (Thread.interrupted()) {
                        localQueue.addAll(0, sendLater);
                        throw new InterruptedException();
                    }

                    // take the first change of the queue
                    Change localChange = localQueue.remove(0);
                        // check if there's a pending change with the same key
                    if (pendingChanges.containsKey(localChange.getKey())) {
                        // we have a change for this key that has not been acked
                        // so send it later
                        sendLater.add(localChange);
                        // let's get the next change
                    } else {
                        try {
                            // add the change to pending changes
                            pendingChanges.put(localChange.getKey(), localChange);
                            // send the change to simperium, if the change ends up being empty
                            // then we'll just skip it
                            sendChange(localChange);
                            localChange.setOnRetryListener(this);
                            // starts up the timer
                            mRetryTimer.scheduleAtFixedRate(localChange.getRetryTimer(), RETRY_DELAY_MS, RETRY_DELAY_MS);
                        } catch (ChangeNotSentException e) {
                            pendingChanges.remove(localChange.getKey());
                        }
                    }
                }
                localQueue.addAll(0, sendLater);
            }
        }

        private void resendPendingChanges(){
            if (mRetryTimer == null) {
                mRetryTimer = new Timer();
            }
            synchronized(mLock){
                // resend all pending changes
                for (Map.Entry<String, Change> entry : pendingChanges.entrySet()) {
                    Change change = entry.getValue();
                    change.setOnRetryListener(this);
                    mRetryTimer.scheduleAtFixedRate(change.getRetryTimer(), RETRY_DELAY_MS, RETRY_DELAY_MS);
                }
            }
        }

        @Override
        public void onRetry(Change change){
            log(LOG_DEBUG, String.format("Retrying change %s", change.getChangeId()));
            try {
                sendChange(change);
            } catch (ChangeNotSentException e) {
                // do nothing the timer will trigger another send
            }
        }

        private void sendChange(Change change)
        throws ChangeNotSentException {
            // send the change down the socket!
            if (!connected) {
                // channel is not initialized, send on reconnect
                return;
            }

            try {
                log(LOG_DEBUG, String.format("Sending change for id: %s op: %s ccid: %s", change.getKey(), change.getOperation(), change.getChangeId()));
                sendMessage(String.format("c:%s", change.toJSONObject()));
                serializer.onSendChange(change);
                change.setSent();
            } catch (ChangeEmptyException e) {
                completeAndDequeueChange(change);
            } catch (ChangeException e) {
                android.util.Log.e(TAG, "Could not send change", e);
                throw new ChangeNotSentException(change, e);
            }

        }

    }

    private void completeAndDequeueChange(Change change) {
        change.setComplete();
        change.resetTimer();
        serializer.onDequeueChange(change);
    }

    public static Map<String,Object> convertJSON(JSONObject json){
        Map<String,Object> map = new HashMap<String,Object>(json.length());
        Iterator keys = json.keys();
        while(keys.hasNext()){
            String key = (String)keys.next();
            try {
                Object val = json.get(key);
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
                Logger.log(TAG, String.format("Failed to convert JSON: %s", e.getMessage()), e);
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
