package com.simperium.client;

import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;

public class Change<T extends Bucket.Syncable> {

    public interface OnRetryListener {
        public void onRetryChange(Change change);
    }

    private String operation;
    private String key;
    private Integer version;
    private Map<String,Object> origin;
    private Map<String,Object> target;
    private String ccid;
    private RetryTimer retryTimer;
    private boolean pending = true;
    private RemoteChange remoteChange;
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
    protected static <T extends Bucket.Syncable> Change<T> buildChange(T object, Map<String,Object> properties){
        return new Change<T>(
            (String)  properties.get(OPERATION_KEY),
            object,
            (String)  properties.get(ID_KEY),
            (Integer) properties.get(SOURCE_VERSION_KEY),
            (Map<String,Object>) properties.get(ORIGIN_KEY),
            (Map<String,Object>) properties.get(TARGET_KEY)
        );
    }
    
    protected Change(String operation, T object, Map<String,Object> origin){
        this(
            operation,
            object,
            object.getSimperiumKey(),
            object.getVersion(),
            object.getUnmodifiedValue(),
            object.getDiffableValue()
        );
    }

    protected Change(String operation, T object){
        this(operation, object, object.getUnmodifiedValue());
    }

    public Change(String operation, T object, String key, Integer sourceVersion, Map<String,Object> origin, Map<String,Object> target){
        super();
        this.operation = operation;
        this.object = object;
        this.ccid = Simperium.uuid();
        this.retryTimer = new RetryTimer();
        this.key = object.getSimperiumKey();
        if (operation != OPERATION_REMOVE) {
            this.version = object.getVersion();
            this.origin = Bucket.deepCopy(origin);
            this.target = Bucket.deepCopy(target);
        }
    }

    public T getObject(){
        return this.object;
    }

    public boolean isPending(){
        if (remoteChange == null) {
            return true;
        } else {
            return !remoteChange.isApplied();
        }
    }

    final protected void setRemoteChange(RemoteChange remoteChange){
        Simperium.log(String.format("Change acknowledged: %s", ccid));
        this.remoteChange = remoteChange;
        stopRetryTimer();
    }

    protected boolean acknowledges(RemoteChange remoteChange){
        if(remoteChange.isAcknowledgedBy(this)){
            setRemoteChange(remoteChange);
            remoteChange.setChange(this);
            return true;
        }
        return false;
    }

    protected boolean keyMatches(Change otherChange){
        return otherChange.getKey().equals(getKey());
    }

    protected String getKey(){
        return key;
    }

    protected String getChangeId(){
        return this.ccid;
    }

    protected Map<String,Object> getOrigin(){
        return origin;
    }

    protected Map<String,Object> getTarget(){
        return target;
    }

    protected String getOperation(){
        return operation;
    }

    protected Integer getVersion(){
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

    protected void setOnRetryListener(OnRetryListener listener){
        retryTimer.setOnRetryListener(listener);
    }

    protected void stopRetryTimer(){
        retryTimer.cancel();
    }

    protected RetryTimer getRetryTimer(){
        return retryTimer;
    }

    /**
     * The change message requires a diff value in the JSON payload
     */
    protected Boolean requiresDiff(){
        return operation.equals(OPERATION_MODIFY);
    }

    /**
     * Creates a new change with the given sourceVersion and origin
     */
    protected Change<T> reapplyOrigin(Integer sourceVersion, Map<String,Object> origin){
        return new Change<T>(operation, object, origin);
    }
    
    /**
     * For attempting retries
     */
    private class RetryTimer extends TimerTask {
        private OnRetryListener listener;
        private Change change;

        protected RetryTimer(){
            super();
        }
        protected void setOnRetryListener(OnRetryListener listener){
            this.listener = listener;
        }
        public void run(){
            Simperium.log(String.format("Retry change: %s", Change.this.getChangeId()));
            if (listener != null) {
                listener.onRetryChange(Change.this);
            }
        }

    }
    

}