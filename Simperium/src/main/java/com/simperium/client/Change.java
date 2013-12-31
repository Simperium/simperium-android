package com.simperium.client;

import com.simperium.util.JSONDiff;
import com.simperium.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

import static com.simperium.util.Uuid.uuid;

public class Change {

    public static final String TAG="Simperium.Change";

    public interface OnRetryListener {
        public void onRetry(Change change);
    }

    public interface OnAcknowledgedListener {
        public void onAcknowledged(Change change);
    }

    public interface OnCompleteListener {
        public void onComplete(Change change);
    }

    public static final String OPERATION_MODIFY   = "M";
    public static final String OPERATION_REMOVE   = JSONDiff.OPERATION_REMOVE;
    public static final String ID_KEY             = "id";
    public static final String CHANGE_ID_KEY      = "ccid";
    public static final String SOURCE_VERSION_KEY = "sv";
    public static final String TARGET_KEY         = "target";
    public static final String ORIGIN_KEY         = "origin";
    public static final String OPERATION_KEY      = "o";
    public static final String OBJECT_DATA_KEY    = "d";

    private String operation;
    private String key, bucketName;
    private Integer version;
    private JSONObject origin;
    private JSONObject target;
    private String ccid;
    private boolean pending = true, acknowledged = false, sent = false;
    private OnRetryListener retryListener;
    private OnCompleteListener completeListener;
    private OnAcknowledgedListener acknowledgedListener;
    private Change compressed;
    private JSONDiff jsondiff = new JSONDiff();
    private boolean sendFullObject = false;
    private TimerTask retryTimer;

    /**
     * Constructs a change object from a map of values
     */
    public static Change buildChange(Syncable object, JSONObject properties)
    throws JSONException {
        return new Change(
            properties.getString(OPERATION_KEY),
            object.getBucketName(),
            object.getSimperiumKey(),
            properties.getInt(SOURCE_VERSION_KEY),
            properties.getJSONObject(ORIGIN_KEY),
            properties.getJSONObject(TARGET_KEY)
        );
    }

    public static Change buildChange(String operation, String ccid, String bucketName, String key, Integer version, JSONObject origin, JSONObject target){
        return new Change(operation, ccid, bucketName, key, version, origin, target);
    }

    private Change(String operation, Syncable object, JSONObject origin){
        this(operation, object.getBucketName(), object.getSimperiumKey(), object.getVersion(),
            origin, object.getDiffableValue());
    }

    private Change(String operation, String bucketName, String key, Integer sourceVersion, JSONObject origin, JSONObject target, Change compressed){
        this(operation, bucketName, key, sourceVersion, origin, target);
        this.compressed = compressed;
    }

    public Change(String operation, Syncable object){
        this(operation, object, object.getUnmodifiedValue());
    }

    protected Change(String operation, String bucketName, String key, Integer sourceVersion, JSONObject origin, JSONObject target){
        this(operation, uuid(), bucketName, key, sourceVersion, origin, target);
    }

    protected Change(String operation, String ccid, String bucketName, String key, Integer sourceVersion, JSONObject origin, JSONObject target){
        this.operation = operation;
        this.ccid = ccid;
        this.bucketName = bucketName;
        this.key = key;
        if (!operation.equals(OPERATION_REMOVE)) {
            this.version = sourceVersion;
            this.origin = JSONDiff.deepCopy(origin);
            this.target = JSONDiff.deepCopy(target);
        }

        this.resetTimer();
    }

    public boolean isModifyOperation(){
        return operation.equals(OPERATION_MODIFY);
    }

    public boolean isRemoveOperation() {
        return operation.equals(OPERATION_REMOVE);
    }

    public boolean isPending(){
        return pending;
    }

    public boolean isSent(){
        return sent;
    }

    public boolean isComplete(){
        return !pending;
    }

    public boolean isAcknowledged(){
        return acknowledged;
    }

    protected void setAcknowledged(){
        acknowledged = true;
        resetTimer();
        if (acknowledgedListener != null) {
            acknowledgedListener.onAcknowledged(this);
        }
        if (compressed != null) {
            compressed.setAcknowledged();
        }
    }

    protected void setComplete(){
        pending = false;
        if (completeListener != null) {
            completeListener.onComplete(this);
        }
        if (compressed != null) {
            compressed.setComplete();
        }
    }

    protected void setSent(){
        sent = true;
        if (compressed != null) {
            compressed.setSent();
        }
    }

    protected boolean keyMatches(Change otherChange){
        return otherChange.getKey().equals(getKey());
    }

    public String getKey(){
        return key;
    }

    public String getBucketName(){
        return bucketName;
    }

    public String getChangeId(){
        return this.ccid;
    }

    public JSONObject getOrigin(){
        return origin;
    }

    public JSONObject getTarget(){
        return target;
    }

    public String getOperation(){
        return operation;
    }

    public Integer getVersion(){
        return version;
    }

    public void setSendFullObject(boolean sendFullObject) {
        this.sendFullObject = sendFullObject;
    }

    public JSONObject toJSONObject()
    throws ChangeEmptyException, ChangeInvalidException {
        try {
            JSONObject json = new JSONObject();
            json.put(ID_KEY, getKey());
            json.put(CHANGE_ID_KEY, getChangeId());
            json.put(JSONDiff.DIFF_OPERATION_KEY, getOperation());

            Integer vresion = getVersion();
            if (version != null && version > 0) {
                json.put(SOURCE_VERSION_KEY, version);
            }

            JSONObject diff = getDiff();
            boolean requiresDiff = requiresDiff();

            if (requiresDiff && diff.length() == 0) {
                throw new ChangeEmptyException(this);
            }

            if (requiresDiff) {
                json.put(JSONDiff.DIFF_VALUE_KEY, diff.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
            }

            if (sendFullObject) {
                json.put(OBJECT_DATA_KEY, target);
            }

            return json;
        } catch (JSONException e) {
            throw new ChangeInvalidException(this, "Could not build change JSON", e);
        }
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

    public void setOnAcknowledgedListener(OnAcknowledgedListener listener){
        acknowledgedListener = listener;
    }

    public void setOnCompleteListener(OnCompleteListener listener){
        
    }

    protected void setOnRetryListener(OnRetryListener listener){
        retryListener = listener;
    }

    protected void resetTimer(){
        if (retryTimer != null) {
            retryTimer.cancel();
        }

        retryTimer = new TimerTask(){
            @Override
            public void run(){
                Logger.log("Simperium.Channel", String.format("Retry change: %s", Change.this));
                if (retryListener != null) {
                    retryListener.onRetry(Change.this);
                }
            }
        };
    }

    protected TimerTask getRetryTimer(){
        return retryTimer;
    }

    public String toString(){
        return String.format("Change %s %s %s", getChangeId(), getKey(), operation);
    }
    /**
     * The change message requires a diff value in the JSON payload
     */
    public boolean requiresDiff(){
        return operation.equals(OPERATION_MODIFY);
    }

    public JSONObject getDiff(){
        try {
            return jsondiff.diff(origin, target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new change with the given sourceVersion and origin
     */
    protected Change reapplyOrigin(Integer sourceVersion, JSONObject origin){
        // protected Change(String operation, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target){
        return new Change(operation, bucketName, key, sourceVersion, origin, target, this);
    }

}