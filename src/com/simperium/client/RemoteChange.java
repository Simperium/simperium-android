package com.simperium.client;

import com.simperium.client.Simperium;
import com.simperium.util.JSONDiff;
import com.simperium.util.Logger;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates parsing and logic for remote changes
 */
class RemoteChange {
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
    private boolean applied = false;
    private Change change;
    /**
     * All remote changes include clientid, key and ccids then these differences:
     * - errors have error key
     * - changes with operation "-" do not have a value
     * - changes with operation "M" have a v (value), ev (entity version) and if not a new object an sv (source version)
     */
    protected RemoteChange(String clientid, String key, List<String> ccids, String changeVersion, Integer sourceVersion, Integer entityVersion, String operation, Map<String,Object> value){
        this.clientid = clientid;
        this.key = key;
        this.ccids = ccids;
        this.sourceVersion = sourceVersion;
        this.entityVersion = entityVersion;
        this.operation = operation;
        this.value = value;
        this.changeVersion = changeVersion;
    }

    protected RemoteChange(String clientid, String key, List<String> ccids, Integer errorCode){
        this.clientid = clientid;
        this.key = key;
        this.ccids = ccids;
        this.errorCode = errorCode;
    }

    protected boolean isAcknowledged(){
        return change != null;
    }

    protected boolean isApplied(){
        return applied;
    }

    protected void setApplied(){
        if (applied == false) {
            applied = true;
            change.setComplete();
        }
    }

    protected boolean isAcknowledgedBy(Change change){
        if (change == null) return false;
        // if we have a Change with the same change id from the same client id
        // then we were waiting for this change
        if(hasChangeId(change) && getClientId().equals(Simperium.CLIENT_ID)){
            this.change = change;
            change.setAcknowledged();
            return true;
        }
        return false;
    }

    protected boolean isError(){
        return errorCode != null;
    }

    protected boolean isRemoveOperation(){
        return operation.equals(OPERATION_REMOVE);
    }

    protected boolean isModifyOperation(){
        return operation.equals(OPERATION_MODIFY);
    }

    protected boolean isAddOperation(){
        return isModifyOperation() && sourceVersion == null;
    }

    protected Integer getErrorCode(){
        return errorCode;
    }

    protected boolean isNew(){
        return !isError() && sourceVersion == null;
    }

    protected String getKey(){
        return key;
    }

    protected String getClientId(){
        return clientid;
    }

    protected Integer getSourceVersion(){
        return sourceVersion;
    }

    protected Integer getObjectVersion(){
        return entityVersion;
    }

    protected String getChangeVersion(){
        return changeVersion;
    }

    protected Map<String,Object> getPatch(){
        return value;
    }

    protected boolean hasChangeId(String ccid){
        return ccids.contains(ccid);
    }

    protected boolean hasChangeId(Change change){
        return hasChangeId(change.getChangeId());
    }

    protected static RemoteChange buildFromMap(Map<String,Object> changeData){
        // get the list of ccids that this applies to
        List<String> ccids = (List<String>)changeData.get(CHANGE_IDS_KEY);
        // get the client id
        String client_id = (String)changeData.get(CLIENT_KEY);
        // get the id of the object it applies to
        String id = (String)changeData.get(ID_KEY);
        if (changeData.containsKey(ERROR_KEY)) {
            Integer errorCode = (Integer)changeData.get(ERROR_KEY);
            Logger.log(String.format("Received error for change: %d", errorCode, changeData));
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