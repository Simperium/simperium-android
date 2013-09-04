package com.simperium.client;

import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;

import com.simperium.util.JSONDiff;
import com.simperium.util.Logger;
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

    private String operation;
    private String key, bucketName;
    private Integer version;
    private Map<String,Object> origin;
    private Map<String,Object> target;
    private String ccid;
    private boolean pending = true, acknowledged = false, sent = false;
    private OnRetryListener retryListener;
    private OnCompleteListener completeListener;
    private OnAcknowledgedListener acknowledgedListener;
    private Change compressed;
    private JSONDiff jsondiff = new JSONDiff();

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
    public static Change buildChange(Syncable object, Map<String,Object> properties){
        return new Change(
            (String)  properties.get(OPERATION_KEY),
            object.getBucketName(),
            object.getSimperiumKey(),
            (Integer) properties.get(SOURCE_VERSION_KEY),
            (Map<String,Object>) properties.get(ORIGIN_KEY),
            (Map<String,Object>) properties.get(TARGET_KEY)
        );
    }

    public static Change buildChange(String operation, String ccid, String bucketName, String key, Integer version, Map<String,Object> origin, Map<String,Object> target){
        return new Change(operation, ccid, bucketName, key, version, origin, target);
    }

    private Change(String operation, Syncable object, Map<String,Object> origin){
        this(operation, object.getBucketName(), object.getSimperiumKey(), object.getVersion(),
            origin, object.getDiffableValue());
    }

    private Change(String operation, String bucketName, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target, Change compressed){
        this(operation, bucketName, key, sourceVersion, origin, target);
        this.compressed = compressed;
    }

    public Change(String operation, Syncable object){
        this(operation, object, object.getUnmodifiedValue());
    }

    protected Change(String operation, String bucketName, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target){
        this(operation, uuid(), bucketName, key, sourceVersion, origin, target);
    }

    protected Change(String operation, String ccid, String bucketName, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target){
        this.operation = operation;
        this.ccid = ccid;
        this.bucketName = bucketName;
        this.key = key;
        if (operation != OPERATION_REMOVE) {
            this.version = sourceVersion;
            this.origin = JSONDiff.deepCopy(origin);
            this.target = JSONDiff.deepCopy(target);
        }
    }

    public boolean isModifyOperation(){
        return operation.equals(OPERATION_MODIFY);
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
        stopRetryTimer();
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

    protected void stopRetryTimer(){
        retryTimer.cancel();
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
    public Boolean requiresDiff(){
        return operation.equals(OPERATION_MODIFY);
    }

    public Map<String,Object> getDiff(){
        return jsondiff.diff(origin, target);
    }

    /**
     * Creates a new change with the given sourceVersion and origin
     */
    protected Change reapplyOrigin(Integer sourceVersion, Map<String,Object> origin){
        // protected Change(String operation, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target){
        return new Change(operation, bucketName, key, sourceVersion, origin, target, this);
    }
    
    private TimerTask retryTimer = new TimerTask(){
        @Override
        public void run(){
            Logger.log("Simperium.Channel", String.format("Retry change: %s", Change.this));
            if (retryListener != null) {
                retryListener.onRetry(Change.this);
            }
        }
    };

}