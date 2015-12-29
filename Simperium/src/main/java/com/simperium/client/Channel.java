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

import com.simperium.BuildConfig;
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
import java.util.TreeMap;
import java.util.concurrent.Executor;

import android.util.Log;

public class Channel implements Bucket.Channel {

    public static class ChangeNotSentException extends ChangeException {

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
    static final String EXPIRED_AUTH_CODE_KEY = "code";
    static final int EXPIRED_AUTH_MALFORMED_TOKEN_CODE = 400;
    static final int EXPIRED_AUTH_INVALID_TOKEN_CODE = 401;

    static public final int LOG_DEBUG = ChannelProvider.LOG_DEBUG;

    // Parameters for querying bucket
    static final Integer INDEX_PAGE_SIZE  = 50;

    // Constants for parsing command messages
    static final Integer MESSAGE_PARTS = 2;
    static final Integer COMMAND_PART  = 0;
    static final String  COMMAND_FORMAT = "%s:%s";

    // bucket determines which mBucket we are using on this channel
    private Bucket mBucket;
    // the object the receives the messages the channel emits
    private OnMessageListener mListener;
    // track channel status
    protected boolean mStarted = false, mConnected = false, mStartOnConnect = false, mIdle = true;
    private boolean mHaveIndex = false;
    private CommandInvoker mCommands = new CommandInvoker();
    private String mAppId, mSessionId;
    private Serializer mSerializer;
    protected Executor mExecutor;

    // for sending and receiving changes
    final private ChangeProcessor mChangeProcessor;
    private IndexProcessor mIndexProcessor;
    
    public interface Serializer {
        // public <T extends Syncable> void save(Bucket<T> mBucket, SerializedQueue<T> data);
        SerializedQueue restore(Bucket mBucket);
        void reset(Bucket mBucket);
        void onQueueChange(Change change);
        void onDequeueChange(Change change);
        void onSendChange(Change change);
        void onAcknowledgeChange(Change change);
    }

    public static class SerializedQueue {
        final public Map<String,Change> pending;
        final public List<Change> queued;

        public SerializedQueue() {
            this(new HashMap<String, Change>(), new ArrayList<Change>());
        }

        public SerializedQueue(Map<String,Change> pendingChanges, List<Change> queuedChanges) {
            this.pending = pendingChanges;
            this.queued = queuedChanges;
        }
    }

    public Channel(Executor executor, String appId, String sessionId, final Bucket bucket, Serializer serializer, OnMessageListener listener) {
        mExecutor = executor;
        mSerializer = serializer;
        mAppId = appId;
        mSessionId = sessionId;
        mBucket = bucket;
        mListener = listener;
        // Receive auth: command
        command(COMMAND_AUTH, new Command() {
            public void execute(String param) {
                User user = getUser();
                // ignore auth:expired, implement new auth:{JSON} for failures
                if(EXPIRED_AUTH.equals(param.trim())) return;
                // if it starts with { let's see if it's error JSON
                if (param.indexOf(EXPIRED_AUTH_INDICATOR) == 0) {
                    try {
                        JSONObject authResponse = new JSONObject(param);
                        int code = authResponse.getInt(EXPIRED_AUTH_CODE_KEY);
                        if (code == EXPIRED_AUTH_INVALID_TOKEN_CODE || code == EXPIRED_AUTH_MALFORMED_TOKEN_CODE) {
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
        command(COMMAND_INDEX, new Command() {

            @Override
            public void execute(String param) {
                updateIndex(param);
            }

        });

        // Receive c: command
        command(COMMAND_CHANGE, new Command() {

            @Override
            public void execute(String param) {
                handleRemoteChanges(param);
            }

        });

        // Receive e: command
        command(COMMAND_ENTITY, new Command() {

            @Override
            public void execute(String param) {
                handleVersionResponse(param);
            }

        });

        // Receive index command
        command(COMMAND_INDEX_STATE, new Command() {

            @Override
            public void execute(String param) {
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

        mChangeProcessor = new ChangeProcessor();
    }

    public String toString() {
        return String.format("%s<%s>", super.toString(), mBucket.getName());
    }

    protected Ghost onAcknowledged(RemoteChange remoteChange, Change acknowledgedChange)
    throws RemoteChangeInvalidException {
        // if this isn't a removal, update the ghost for the relevant object
        return mBucket.acknowledgeChange(remoteChange, acknowledgedChange);
    }

    /**
     * Handle errors from simperium service
     * see <a href="https://gist.github.com/beaucollins/6998802#error-responses">Error Responses</a>
     */
    protected void onError(RemoteChange remoteChange, Change erroredChange) {
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
    public void reset() {
        mChangeProcessor.reset();
    }

    private boolean hasChangeVersion() {
        return mBucket.hasChangeVersion();
    }

    private String getChangeVersion() {
        return mBucket.getChangeVersion();
    }

    private void getLatestVersions() {
        mHaveIndex = false;
        // initialize the new query for new index data
        IndexQuery query = new IndexQuery();
        // send the i:::: messages
        sendMessage(query.toString());
    }
    /**
     * Diffs and object's local modifications and queues up the changes
     */
    public Change queueLocalChange(String simperiumKey) {
        Change change = new Change(Change.OPERATION_MODIFY, this.getBucketName(), simperiumKey);
        mChangeProcessor.addChange(change);
        return change;
    }

    public Change queueLocalDeletion(Syncable object) {
        Change change = new Change(Change.OPERATION_REMOVE, this.getBucketName(), object.getSimperiumKey());
        mChangeProcessor.addChange(change);
        return change;
    }

    public void requeueChangeWithFullObject(Change change) {
        // Don't requeue this change if we've retried past the allowed limit.
        if (change.getRetryCount() >= RETRY_LIMIT) {
            completeAndDequeueChange(change);
            return;
        }

        change.incrementRetryCount();
        change.setSendFullObject(true);
        mChangeProcessor.addChange(change);
    }

    private static final String INDEX_CURRENT_VERSION_KEY = "current";
    private static final String INDEX_VERSIONS_KEY = "index";
    private static final String INDEX_MARK_KEY = "mark";

    private void updateIndex(String indexJson) {
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
        if (mIndexProcessor == null || !mIndexProcessor.addIndexPage(index)) {
            // make sure we're not processing changes and clear pending changes
            String currentIndex;
            try {
                currentIndex = index.getString(INDEX_CURRENT_VERSION_KEY);
            } catch(JSONException e) {
                // we have an empty index
                currentIndex = "";
            }

            mIndexProcessor = new IndexProcessor(getBucket(), currentIndex, mIndexProcessorListener);
            mIndexProcessor.start(index);
        } else {
            // received an index page for a different change version
            // TODO: reconcile the out of band index cv
        }
    }

    private IndexProcessorListener mIndexProcessorListener = new IndexProcessorListener() {
        @Override
        public void onComplete(String cv) {
            mHaveIndex = true;
            mIndexProcessor = null;
            mChangeProcessor.start();
        }
    };

    private void handleRemoteChanges(String changesJson) {
        JSONArray changes;
        if (changesJson.equals(RESPONSE_UNKNOWN)) {
            // noop API 1.1 does not send "?" here
            return;
        }
        try {
            changes = new JSONArray(changesJson);
        } catch (JSONException e) {
            Logger.log(TAG, "Failed to parse remote changes JSON", e);
            return;
        }
        // Loop through each change? Convert changes to array list
        mChangeProcessor.addChanges(changes);
    }

    private static final String ENTITY_DATA_KEY = "data";
    private void handleVersionResponse(String versionData) {

        try {
            // versionData will be: key.version\n{"data":ENTITY}
            ObjectVersionData objectVersion = ObjectVersionData.parseString(versionData);

            if (mIndexProcessor != null) {
                mIndexProcessor.addObjectData(objectVersion);
            }

            // if we have any revision requests pending, we want to collect the objects
            boolean collected = false;
            for (RevisionsCollector collector : revisionCollectors) {
                if (collector.addObjectData(objectVersion)) {
                    collected = true;
                }
            }

            if (!collected) {
                updateBucketWithObjectVersion(objectVersion);
            }
        } catch (ObjectVersionUnknownException e) {
            reportRevisionsError();
            log(LOG_DEBUG, String.format(Locale.US, "Object version does not exist %s", e.version));
        } catch (ObjectVersionDataInvalidException e) {
            reportRevisionsError();
            log(LOG_DEBUG, String.format(Locale.US, "Object version JSON data malformed %s", e.version));
        } catch (ObjectVersionParseException e) {
            reportRevisionsError();
            log(LOG_DEBUG, String.format(Locale.US, "Received invalid object version: %s", e.versionString));
        }
    }

    private void updateBucketWithObjectVersion(ObjectVersionData objectVersion) {
        Ghost ghost = new Ghost(objectVersion.getKey(), objectVersion.getVersion(),
            objectVersion.getData());
        mBucket.updateGhost(ghost, null);
    }

    private boolean reportRevisionsError() {
        if (revisionCollectors.size() > 0) {
            for (RevisionsCollector collector : revisionCollectors) {
                // Decrease the expected amount of revisions if we encountered an error
                collector.decreaseRevisionsCount();
            }

            return true;
        }

        return false;
    }

    private void abortRevisionsCollection(Exception e) {
        if (revisionCollectors.size() > 0) {
            for (RevisionsCollector collector : revisionCollectors) {
                if (collector.getCallbacks() != null) {
                    collector.getCallbacks().onError(e);
                }
            }

            revisionCollectors.clear();
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
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {

                JSONArray objectVersions = new JSONArray();
                String idKey = "id";
                String versionKey = "v";
                Bucket.ObjectCursor objects = mBucket.allObjects();

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
                Collection<Change> pending = mChangeProcessor.pendingChanges();
                JSONArray pendingData = new JSONArray();
                for (Change change : pending) {
                    try {
                        JSONObject changeData = new JSONObject();
                        changeData.put("id", change.getKey());
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
                    extra.put("bucketName", mBucket.getName());
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

    public Bucket getBucket() {
        return mBucket;
    }

    public String getBucketName() {
        if (mBucket != null) {
            return mBucket.getName();
        }
        return "";
    }

    public User getUser() {
        return mBucket.getUser();
    }

    public String getSessionId() {
        return mSessionId;
    }

    public boolean isStarted() {
        return mStarted;
    }

    public boolean isConnected() {
        return mConnected;
    }

    /**
     * ChangeProcessor has no work to do
     */
    public boolean isIdle() {
        return mIdle;
    }

    @Override
    public void getRevisions(final String key, final int sinceVersion, final int maxVersionCount,
                             final Bucket.RevisionsRequestCallbacks callbacks) {
        // for the key and version iterate down requesting the each version for the object
        RevisionsCollector collector = new RevisionsCollector(key, sinceVersion, maxVersionCount, callbacks);
        revisionCollectors.add(collector);
        collector.send();
    }

    public void log(int level, CharSequence message) {
        if (this.mListener != null) {
            this.mListener.onLog(this, level, message);
        }
    }

    /**
     * Send Bucket's init message to start syncing changes.
     */
    @Override
    public void start() {
        if (mStarted) {
            // we've already mStarted
            return;
        }
        // If socket isn't mConnected yet we have to wait until connection
        // is up and try starting then
        if (!mConnected) {
            if (mListener != null) mListener.onOpen(this);
            mStartOnConnect = true;
            return;
        }
        if (!mBucket.getUser().hasAccessToken()) {
            // we won't connect unless we have an access token
            return;
        }

        mStarted = true;

        Object initialCommand;
        if (!hasChangeVersion()) {
            // the mBucket has never gotten an index
            mHaveIndex = false;
            initialCommand = new IndexQuery();
        } else {
            // retive changes since last cv
            mHaveIndex = true;
            initialCommand = String.format("%s:%s", COMMAND_VERSION, getChangeVersion());
        }

        // Build the required json object for initializing
        HashMap<String,Object> init = new HashMap<>(6);
        init.put(FIELD_API_VERSION, SIMPERIUM_API_VERSION);
        init.put(FIELD_CLIENT_ID, mSessionId);
        init.put(FIELD_APP_ID, mAppId);
        init.put(FIELD_AUTH_TOKEN, mBucket.getUser().getAccessToken());
        init.put(FIELD_BUCKET_NAME, mBucket.getRemoteName());
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
    public void stop() {
        mStartOnConnect = false;
        mStarted = false;
        if (mListener != null) {
            mListener.onClose(this);
        }
    }

    // websocket
    public void onConnect() {
        mConnected = true;
        Logger.log(TAG, String.format("onConnect autoStart? %b", mStartOnConnect));
        if(mStartOnConnect) start();
    }

    public void onDisconnect() {
        mStarted = false;
        mConnected = false;
    }
    /**
     * Receive a message from the WebSocketManager which already strips the channel
     * prefix from the message.
     */
    public void receiveMessage(String message) {
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
    private void sendMessage(String message) {
        // send a message
        if (mListener != null) {
            MessageEvent event = new MessageEvent(this, message);
            mListener.onMessage(event);
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

    private void command(String name, Command command) {
        mCommands.add(name, command);
    }

    private void executeCommand(String name, String params) {
        mCommands.executeCommand(name, params);
    }

    /**
     * Command and CommandInvoker provide a declaritive syntax for handling commands that come in
     * from Channel.onMessage. Takes a message like "auth:user@example.com" and finds the correct
     * command to run and stips the command from the message so the command can take care of
     * processing the params.
     *
     *      channel.command("auth", new Command() {
     *         public void onRun(String params) {
     *           // params is now either an email address or "expired"
     *         }
     *      });
     */
    private interface Command {
        void execute(String params);
    }

    private class CommandInvoker {
        private HashMap<String,Command> mCommands = new HashMap<>();

        protected CommandInvoker add(String name, Command command) {
            mCommands.put(name, command);
            return this;
        }

        protected void executeCommand(String name, String params) {
            if (mCommands.containsKey(name)) {
                Command command = mCommands.get(name);
                command.execute(params);
            } else {
                Logger.log(TAG, String.format("Unkown command received: %s", name));
            }
        }
    }

    static final String CURSOR_FORMAT = "%s::%s::%s";
    /**
     * IndexQuery provides an interface for managing a query cursor and limit fields.
     * TODO: add a way to build an IndexQuery from an index response
     */
    private class IndexQuery {

        private String mMark = "";
        private Integer mLimit = INDEX_PAGE_SIZE;

        public IndexQuery() {}

        public IndexQuery(String mark) {
            this(mark, INDEX_PAGE_SIZE);
        }

        public IndexQuery(String mark, Integer limit) {
            mMark = mark;
            mLimit = limit;
        }

        public String toString() {
            String limitString = "";
            if (mLimit > -1) {
                limitString = mLimit.toString();
            }
            return String.format(CURSOR_FORMAT, COMMAND_INDEX, mMark, limitString);
        }

    }

    public static class ObjectVersion {

        public final String key;
        public final Integer version;

        public ObjectVersion(String key, Integer version) {
            this.key = key;
            this.version = version;
        }

        public String getKey() {
            return key;
        }

        public Integer getVersion() {
            return version;
        }

        public String toString() {
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

        public JSONObject getData() {
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

    // Collects revisions for a Simperium object
    private class RevisionsCollector implements Bucket.RevisionsRequest {

        final private String key;
        final private int sinceVersion;
        final private int maxVersionCount;
        final private Bucket.RevisionsRequestCallbacks callbacks;
        private boolean completed = true;
        private boolean sent = false;
        private int mTotalRevisions;

        private Map<Integer, Syncable> versionsMap = Collections.synchronizedSortedMap(new TreeMap<Integer, Syncable>());

        RevisionsCollector(String key, int sinceVersion, int maxVersionCount, Bucket.RevisionsRequestCallbacks callbacks) {
            this.key = key;
            this.sinceVersion = sinceVersion;
            this.maxVersionCount = maxVersionCount;
            this.callbacks = callbacks;
        }

        private void send() {
            if (!mConnected) {
                if (callbacks != null) {
                    abortRevisionsCollection(new Exception("Can't retrieve revisions: No connection."));
                }

                return;
            }

            if (!sent) {
                sent = true;
                int minVersion;
                if (maxVersionCount > 0) {
                    minVersion = (sinceVersion - maxVersionCount > 0) ? sinceVersion - maxVersionCount : 1;
                } else {
                    minVersion = 1;
                }
                mTotalRevisions = sinceVersion - minVersion;
                // for each version send an e: request
                for (int i = minVersion; i < sinceVersion; i++) {
                    sendObjectVersionRequest(key, i);
                }
            }
        }

        public boolean addObjectData(ObjectVersionData objectVersionData) {
            int version = objectVersionData.getVersion();
            if (objectVersionData.getKey().equals(this.key) && version < sinceVersion && versionsMap.get(version) == null) {
                versionsMap.put(version, mBucket.buildObject(this.key, objectVersionData.getData()));

                JSONObject data = objectVersionData.getData();
                callbacks.onRevision(key, version, data);

                if (versionsMap.size() == mTotalRevisions) {
                    revisionCollectors.remove(this);
                    completed = true;
                    callbacks.onComplete(versionsMap);
                }
                return true;
            }
            return false;
        }

        public Bucket.RevisionsRequestCallbacks getCallbacks() {
            return callbacks;
        }

        @Override
        public boolean isComplete() {
            return completed;
        }

        public void decreaseRevisionsCount() {
            mTotalRevisions--;
        }
    }

    private void sendObjectVersionRequest(String key, int version){
        sendMessage(String.format("%s:%s.%d", COMMAND_ENTITY, key, version));
    }

    private List<RevisionsCollector> revisionCollectors = Collections.synchronizedList(new ArrayList<RevisionsCollector>());

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

        final private String mCv;
        final private Bucket mBucket;
        private List<String> mQueue = Collections.synchronizedList(new ArrayList<String>());
        private IndexQuery mNextQuery;
        protected boolean mComplete = false, mNotified = false;
        final private IndexProcessorListener mListener;
        protected int mIndexedCount = 0, mReceivedCount = 0;
        final protected Object mCountLock;

        public IndexProcessor(Bucket bucket, String cv, IndexProcessorListener listener) {
            mBucket = bucket;
            mCv = cv;
            mListener = listener;
            mCountLock = new Object();
        }

        /**
         * Receive an object's version data and store it. Send the request for the
         * next object.
         */
        public boolean addObjectData(ObjectVersionData objectVersion) {
            if (!mQueue.remove(objectVersion.toString())) {
                return false;
            }

            // build the ghost and update
            Ghost ghost = new Ghost(objectVersion.getKey(), objectVersion.getVersion(), objectVersion.getData());
            mBucket.addObjectWithGhost(ghost, new Runnable() {
                @Override
                public void run() {
                    synchronized(mCountLock) {
                        mIndexedCount ++;
                        if (mIndexedCount % 10 == 0) {
                            notifyProgress();
                        }
                    }

                    if (mComplete && mReceivedCount == mIndexedCount) {
                        notifyDone();
                    }
                }

            });

            next();

            return true;
        }

        public void start(JSONObject indexPage) {
            addIndexPage(indexPage);
        }

        public void next() {

            // if queue isn't empty, pull it off the top and send request
            if (!mQueue.isEmpty()) {
                String versionString = mQueue.get(0);
                ObjectVersion version;
                try {
                    version = ObjectVersion.parseString(versionString);
                } catch (ObjectVersionParseException e) {
                    Logger.log(TAG, "Failed to parse version string, skipping", e);
                    mQueue.remove(versionString);
                    next();
                    return;
                }

                if (!mBucket.hasKeyVersion(version.getKey(), version.getVersion())) {
                    sendMessage(String.format("%s:%s", COMMAND_ENTITY, version.toString()));
                } else {
                    synchronized(mCountLock) {
                        mIndexedCount ++;
                        Logger.log(TAG, String.format("Already have %s requesting next object", version));
                        mQueue.remove(versionString);
                    }
                    next();
                    return;
                }
                return;
            }

            // if index is empty and we have a next request, make the request
            if (mNextQuery != null) {
                sendMessage(mNextQuery.toString());
                return;
            }

            // no queue, no next query, all done!
            mComplete = true;
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Done receiving object data " + Channel.this);
            }

            if (mReceivedCount == mIndexedCount) {
                notifyDone();
            }

        }

        /**
         * Add the page of data, but only if indexPage cv matches. Detects when it's the
         * last page due to absence of cursor mark
         */
        public Boolean addIndexPage(JSONObject indexPage) {

            String currentIndex;
            try {
                currentIndex = indexPage.getString(INDEX_CURRENT_VERSION_KEY);
            } catch(JSONException e) {
                Logger.log(TAG, String.format("Index did not have current version %s", mCv));
                currentIndex = "";
            }

            if (!currentIndex.equals(mCv)) {
                return false;
            }

            JSONArray indexVersions;
            try {
                indexVersions = indexPage.getJSONArray(INDEX_VERSIONS_KEY);
            } catch(JSONException e) {
                Logger.log(TAG, String.format("Index did not have entities: %s", indexPage));
                return true;
            }

            if (indexVersions.length() > 0) {
                // query for each item that we don't have locally in the mBucket
                for (int i=0; i<indexVersions.length(); i++) {
                    try {

                        JSONObject version = indexVersions.getJSONObject(i);
                        String key  = version.getString(INDEX_OBJECT_ID_KEY);
                        Integer versionNumber = version.getInt(INDEX_OBJECT_VERSION_KEY);
                        ObjectVersion objectVersion = new ObjectVersion(key, versionNumber);
                        mQueue.add(objectVersion.toString());

                        synchronized(mCountLock) {
                            mReceivedCount ++;
                        }

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
                mNextQuery = new IndexQuery(nextMark);
            } else {
                mNextQuery = null;
            }
            next();
            return true;
        }

        synchronized private void notifyDone() {
            if (mNotified) {
                return;
            }
            mNotified = true;
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Notifying index done: " + Channel.this);
            }
            mBucket.indexComplete(mCv);
            mListener.onComplete(mCv);
        }


        private void notifyProgress() {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Notifying index progress: " + Channel.this);
            }
            mBucket.notifyOnNetworkChangeListeners(Bucket.ChangeType.INDEX);
        }
    }

    public boolean haveCompleteIndex() {
        return mHaveIndex;
    }

    /**
     * ChangeProcessor should perform operations on a seperate thread as to not block the websocket
     * ideally it will be a FIFO queue processor so as changes are brought in they can be appended.
     * We also need a way to pause and clear the queue when we download a new index.
     */
    private class ChangeProcessor implements Runnable, Change.OnRetryListener {

        // wait 5 seconds for retries
        public static final long RETRY_DELAY_MS = 5000; 
        private List<JSONObject> mRemoteQueue = Collections.synchronizedList(new ArrayList<JSONObject>(10));
        private List<Change> mLocalQueue = Collections.synchronizedList(new ArrayList<Change>());
        private Map<String,Change> mPendingChanges = Collections.synchronizedMap(new HashMap<String,Change>());
        private Timer mRetryTimer;
        private Thread mThread;
        private final Object mLock = new Object();
        private final Object mRunLock = new Object();

        public ChangeProcessor() {
            restore();
        }

        public Collection<Change> pendingChanges() {
            return mPendingChanges.values();
        }

        private void restore() {
            synchronized(mLock) {
                SerializedQueue serialized = mSerializer.restore(mBucket);
                mLocalQueue.addAll(serialized.queued);
                mPendingChanges.putAll(serialized.pending);
                resendPendingChanges();
            }
        }

        public void addChanges(JSONArray changes) {
            synchronized(mLock) {
                int length = changes.length();
                Logger.log(TAG, String.format("Add remote changes to processor %d", length));
                log(LOG_DEBUG, String.format(Locale.US, "Adding %d remote changes to queue", length));
                for (int i = 0; i < length; i++) {
                    JSONObject change = changes.optJSONObject(i);
                    if (change != null) {
                        mRemoteQueue.add(change);
                    }
                }
                start();
            }
        }

        /**
         * Local change to be queued
         */
        public void addChange(Change change) {
            synchronized(mLock) {
                // compress all changes for this same key
                log(LOG_DEBUG, String.format(Locale.US, "Adding new change to queue %s %s %s",
                    change.getKey(), change.getOperation(), change.getChangeId()));

                Iterator<Change> iterator = mLocalQueue.iterator();
                boolean isModify = change.isModifyOperation();
                while(iterator.hasNext() && isModify) {
                    Change queued = iterator.next();
                    if(queued.getKey().equals(change.getKey())) {
                        mSerializer.onDequeueChange(queued);
                        iterator.remove();
                    }
                }
                mSerializer.onQueueChange(change);
                mLocalQueue.add(change);
            }
            start();
        }

        public void start() {
            // channel must be started and have complete index
            if (!mStarted) {
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

        protected void reset() {
            mPendingChanges.clear();
            mSerializer.reset(mBucket);
        }

        protected void abort() {
            reset();
        }

        /**
         * Check if we have changes we can send out
         */
        protected boolean hasQueuedChanges() {
            synchronized(mLock) {
                Logger.log(TAG, String.format("Checking for queued changes %d", mLocalQueue.size()));
                // if we have have any remote changes to process we have work to do
                if (!mRemoteQueue.isEmpty()) return true;
                // if our local queue is empty we don't have work to do
                if (mLocalQueue.isEmpty()) return false;
                // if we have queued changes, if there's no corresponding pending change then there's still work to do
                for (Change change : mLocalQueue) {
                    if (!mPendingChanges.containsKey(change.getKey())) return true;
                }
                return false;
            }
        }

        public void run() {
            if(!haveCompleteIndex()) return;
            mIdle = false;
            Logger.log(TAG, String.format("%s - Starting change queue", Thread.currentThread().getName()));
            while(true) {
                try {
                    processRemoteChanges();
                    processLocalChanges();
                } catch (InterruptedException e) {
                    // shut down
                    break;
                }
                if(!hasQueuedChanges()) {
                    // we've sent out every change that we can so far, if nothing is pending we can disconnect
                    if (mPendingChanges.isEmpty()) {
                        mIdle = true;
                    }

                    synchronized(mRunLock) {
                        try {
                            Logger.log(TAG, String.format("Waiting <%s> mIdle? %b", mBucket.getName(), mIdle));
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
                Logger.log(TAG, String.format("Processing remote changes %d", mRemoteQueue.size()));
                // bail if thread is interrupted
                while(mRemoteQueue.size() > 0) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    // take an item off the queue
                    RemoteChange remoteChange;
                    try {
                        remoteChange = RemoteChange.buildFromMap(mRemoteQueue.remove(0));
                    } catch (JSONException e) {
                        Logger.log(TAG, "Failed to build remote change", e);
                        continue;
                    }
                    log(LOG_DEBUG, String.format("Processing remote change with cv: %s", remoteChange.getChangeVersion()));
                    // synchronizing on pendingChanges since we're looking up and potentially
                    // removing an entry
                    Change change = mPendingChanges.get(remoteChange.getKey());
                    if (remoteChange.isAcknowledgedBy(change)) {
                        log(LOG_DEBUG, String.format("Found pending change for remote change <%s>: %s", remoteChange.getChangeVersion(), change.getChangeId()));
                        mSerializer.onAcknowledgeChange(change);
                        // change is no longer pending so remove it
                        mPendingChanges.remove(change.getKey());
                        if (remoteChange.isError()) {
                            Logger.log(TAG, String.format("Change error response! %d %s", remoteChange.getErrorCode(), remoteChange.getKey()));
                            onError(remoteChange, change);
                        } else {
                            Ghost ghost = null;
                            try {
                                ghost = onAcknowledged(remoteChange, change);
                            } catch (RemoteChangeInvalidException e) {
                                Logger.log(TAG, "Remote change could not be acknowledged", e);
                                log(LOG_DEBUG, String.format("Failed to acknowledge change <%s> Reason: %s", remoteChange.getChangeVersion(), e.getMessage()));

                                // request the full object for the new version
                                ObjectVersion version = new ObjectVersion(remoteChange.getKey(), remoteChange.getObjectVersion());
                                sendMessage(String.format("%s:%s", COMMAND_ENTITY, version));
                            } finally {
                                Change compressed = null;
                                Iterator<Change> queuedChanges = mLocalQueue.iterator();
                                while (queuedChanges.hasNext()) {
                                    Change queuedChange = queuedChanges.next();
                                    if (queuedChange.getKey().equals(change.getKey())) {
                                        queuedChanges.remove();
                                        if (ghost != null && !remoteChange.isRemoveOperation()) {
                                            compressed = queuedChange;
                                        }
                                    }
                                }
                                if (compressed != null) {
                                    mLocalQueue.add(compressed);
                                }
                            }
                        }
                    } else {
                        if (remoteChange.isError()) {
                            Logger.log(TAG, String.format("Remote change %s was an error but not acknowledged", remoteChange));
                            log(LOG_DEBUG, String.format("Received error response for change but not waiting for any ccids <%s>", remoteChange.getChangeVersion()));
                        } else {
                            try {
                                mBucket.applyRemoteChange(remoteChange);
                                Logger.log(TAG, String.format("Succesfully applied remote change <%s>", remoteChange.getChangeVersion()));
                            } catch (RemoteChangeInvalidException e) {
                                Logger.log(TAG, "Remote change could not be applied", e);
                                log(LOG_DEBUG, String.format("Failed to apply change <%s> Reason: %s", remoteChange.getChangeVersion(), e.getMessage()));

                                dequeueLocalChangesForKey(remoteChange.getKey());
                                // request the full object for the new version
                                ObjectVersion version = new ObjectVersion(remoteChange.getKey(), remoteChange.getObjectVersion());
                                sendMessage(String.format("%s:%s", COMMAND_ENTITY, version));
                            }
                        }
                    }
                    if (!remoteChange.isError() && remoteChange.isRemoveOperation()) {
                        dequeueLocalChangesForKey(remoteChange.getKey());
                    }
                }
            }
        }

        private void dequeueLocalChangesForKey(String simperiumKey) {
            if (simperiumKey == null) return;

            Iterator<Change> iterator = mLocalQueue.iterator();
            while (iterator.hasNext()) {
                Change queuedChange = iterator.next();
                if (queuedChange.getKey().equals(simperiumKey)) {
                    iterator.remove();
                }
            }
        }

        public void processLocalChanges()
        throws InterruptedException {
            synchronized(mLock) {
                if (mLocalQueue.isEmpty()) {
                    return;
                }
                final List<Change> sendLater = new ArrayList<>();
                // find the first local change whose key does not exist in the pendingChanges and there are no remote changes
                while(!mLocalQueue.isEmpty()) {
                    if (Thread.interrupted()) {
                        mLocalQueue.addAll(0, sendLater);
                        throw new InterruptedException();
                    }

                    // take the first change of the queue
                    Change localChange = mLocalQueue.remove(0);
                        // check if there's a pending change with the same key
                    if (mPendingChanges.containsKey(localChange.getKey())) {
                        // we have a change for this key that has not been acked
                        // so send it later
                        sendLater.add(localChange);
                        // let's get the next change
                    } else {
                        try {
                            // add the change to pending changes
                            mPendingChanges.put(localChange.getKey(), localChange);
                            // send the change to simperium, if the change ends up being empty
                            // then we'll just skip it
                            sendChange(localChange);
                            localChange.setOnRetryListener(this);
                            // starts up the timer
                            mRetryTimer.scheduleAtFixedRate(localChange.getRetryTimer(), RETRY_DELAY_MS, RETRY_DELAY_MS);
                        } catch (ChangeNotSentException e) {
                            mPendingChanges.remove(localChange.getKey());
                        }
                    }
                }
                mLocalQueue.addAll(0, sendLater);
            }
        }

        private void resendPendingChanges() {
            if (mRetryTimer == null) {
                mRetryTimer = new Timer();
            }
            synchronized(mLock) {
                // resend all pending changes
                for (Map.Entry<String, Change> entry : mPendingChanges.entrySet()) {
                    Change change = entry.getValue();
                    change.setOnRetryListener(this);
                    mRetryTimer.scheduleAtFixedRate(change.getRetryTimer(), RETRY_DELAY_MS, RETRY_DELAY_MS);
                }
            }
        }

        @Override
        public void onRetry(Change change) {
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
            if (!mConnected) {
                // channel is not initialized, send on reconnect
                return;
            }

            try {
                log(LOG_DEBUG, String.format("Sending change for id: %s op: %s ccid: %s", change.getKey(), change.getOperation(), change.getChangeId()));
                Syncable target = mBucket.getObjectOrBackup(change.getKey());
                Ghost ghost = mBucket.getGhost(change.getKey());
                sendMessage(String.format("c:%s", change.toJSONObject(target.getDiffableValue(), ghost)));
                mSerializer.onSendChange(change);
                change.setSent();
            } catch (BucketObjectMissingException e) {
                Logger.log("Could not get object to send change");
                completeAndDequeueChange(change);
                throw new ChangeNotSentException(change, e);
            } catch (GhostMissingException e) {
                Logger.log("Could not get ghost to send change");
                completeAndDequeueChange(change);
                throw new ChangeNotSentException(change, e);
            } catch (ChangeEmptyException e) {
                completeAndDequeueChange(change);
                throw new ChangeNotSentException(change, e);
            } catch (ChangeException e) {
                android.util.Log.e(TAG, "Could not send change", e);
                throw new ChangeNotSentException(change, e);
            }

        }

    }

    private void completeAndDequeueChange(Change change) {
        change.setComplete();
        change.resetTimer();
        mSerializer.onDequeueChange(change);
    }

    public static Map<String,Object> convertJSON(JSONObject json) {
        Map<String,Object> map = new HashMap<String,Object>(json.length());
        Iterator keys = json.keys();
        while(keys.hasNext()) {
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

    public static List<Object> convertJSON(JSONArray json) {
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

    public static JSONObject serializeJSON(Map<String,Object>map) {
        JSONObject json = new JSONObject();
        for (String key : map.keySet()) {
            Object val = map.get(key);
            try {
                if (val instanceof Map) {
                    json.put(key, serializeJSON((Map<String, Object>) val));
                } else if (val instanceof List) {
                    json.put(key, serializeJSON((List<Object>) val));
                } else if (val instanceof Change) {
                    json.put(key, serializeJSON(((Change) val).toJSONSerializable()));
                } else {
                    json.put(key, val);
                }
            } catch (JSONException e) {
                Logger.log(TAG, String.format("Failed to serialize %s", val));
            }
        }
        return json;
    }

    public static JSONArray serializeJSON(List<Object>list) {
        JSONArray json = new JSONArray();
        for (Object val : list) {
            if (val instanceof Map) {
                json.put(serializeJSON((Map<String, Object>) val));
            } else if (val instanceof List) {
                json.put(serializeJSON((List<Object>) val));
            } else if (val instanceof Change) {
                json.put(serializeJSON(((Change) val).toJSONSerializable()));
            } else {
                json.put(val);
            }
        }
        return json;
    }

}
