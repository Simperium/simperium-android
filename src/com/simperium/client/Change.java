package com.simperium.client;

import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;

import com.simperium.util.JSONDiff;
import com.simperium.util.Logger;
import static com.simperium.util.Uuid.uuid;


public class Change<T extends Syncable> {
    public interface OnRetryListener<T extends Syncable> {
        public void onRetry(Change<T> change);
    }

    public interface OnAcknowledgedListener<T extends Syncable> {
        public void onAcknowledged(Change<T> change);
    }

    public interface OnCompleteListener<T extends Syncable> {
        public void onComplete(Change<T> change);
    }

    private String operation;
    private String key;
    private Integer version;
    private Map<String,Object> origin;
    private Map<String,Object> target;
    private String ccid;
    private boolean pending = true, acknowledged = false, sent = false;
    private OnRetryListener<T> retryListener;
    private OnCompleteListener<T> completeListener;
    private OnAcknowledgedListener<T> acknowledgedListener;
    private Change<T> compressed;
    private JSONDiff jsondiff = new JSONDiff();
    final private T object;

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
    protected static <T extends Syncable> Change<T> buildChange(T object, Map<String,Object> properties){
        return new Change<T>(
            (String)  properties.get(OPERATION_KEY),
            object,
            (String)  properties.get(ID_KEY),
            (Integer) properties.get(SOURCE_VERSION_KEY),
            (Map<String,Object>) properties.get(ORIGIN_KEY),
            (Map<String,Object>) properties.get(TARGET_KEY)
        );
    }
    
    private Change(String operation, T object, Map<String,Object> origin){
        this(
            operation,
            object,
            object.getSimperiumKey(),
            object.getVersion(),
            object.getUnmodifiedValue(),
            object.getDiffableValue()
        );
    }

    private Change(String operation, T object, Map<String,Object> origin, Change<T> compressed){
        this(operation, object, origin);
        this.compressed = compressed;
    }

    public Change(String operation, T object){
        this(operation, object, object.getUnmodifiedValue());
    }

    protected Change(String operation, T object, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target){
        super();
        this.operation = operation;
        this.object = object;
        this.ccid = uuid();
        this.key = object.getSimperiumKey();
        if (operation != OPERATION_REMOVE) {
            this.version = object.getVersion();
            this.origin = JSONDiff.deepCopy(origin);
            this.target = JSONDiff.deepCopy(target);
        }
    }

    public T getObject(){
        return this.object;
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

    public void setOnAcknowledgedListener(OnAcknowledgedListener<T> listener){
        acknowledgedListener = listener;
    }

    public void setOnCompleteListener(OnCompleteListener<T> listener){
        
    }

    protected void setOnRetryListener(OnRetryListener<T> listener){
        retryListener = listener;
    }

    protected void stopRetryTimer(){
        retryTimer.cancel();
    }

    protected TimerTask getRetryTimer(){
        return retryTimer;
    }

    public String toString(){
        return String.format("Change %s %s", getChangeId(), getKey());
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
    protected Change<T> reapplyOrigin(Integer sourceVersion, Map<String,Object> origin){
        return new Change<T>(operation, object, origin, this);
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