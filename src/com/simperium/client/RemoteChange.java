package com.simperium.client;

import com.simperium.Simperium;
import com.simperium.util.JSONDiff;
import com.simperium.util.Logger;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates parsing and logic for remote changes
 */
public class RemoteChange {
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
    private JSONDiff jsondiff = new JSONDiff();
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

    public RemoteChange(String clientid, String key, List<String> ccids, String changeVersion, Integer sourceVersion, Integer entityVersion, Map<String,Object> diff){
        this(clientid, key, ccids, changeVersion, sourceVersion, entityVersion, (String) diff.get(JSONDiff.DIFF_OPERATION_KEY), (Map<String,Object>)diff.get(JSONDiff.DIFF_VALUE_KEY));
    }

    public RemoteChange(String clientid, String key, List<String> ccids, Integer errorCode){
        this.clientid = clientid;
        this.key = key;
        this.ccids = ccids;
        this.errorCode = errorCode;
    }

    protected Ghost apply(Syncable object) throws RemoteChangeInvalidException {
        Ghost gost = apply(object.getGhost());
        object.setGhost(gost);
        return gost;
    }

    protected Ghost apply(Ghost ghost) throws RemoteChangeInvalidException {
        // keys and versions must match otherwise throw an error
        if (!ghost.getSimperiumKey().equals(getKey())) {
            throw(new RemoteChangeInvalidException(
                    String.format("Local instance key %s does not match change key %s",
                        ghost.getSimperiumKey(), getKey())));
        }
        if (isModifyOperation() && !ghost.getVersion().equals(getSourceVersion())) {
            throw(new RemoteChangeInvalidException(
                    String.format("Local instance of %s has source version of %d and remote change has %d",
                    getKey(), ghost.getVersion(), getSourceVersion())));
        }
        if (isAddOperation() && ghost.getVersion() != 0) {
            throw(new RemoteChangeInvalidException(
                    String.format("Local instance has version greater than 0 with remote add operation")));
        }
        Map<String,Object> properties = jsondiff.apply(ghost.getDiffableValue(), getPatch());
        return new Ghost(getKey(), getObjectVersion(), properties);
    }

    public boolean isAcknowledged(){
        return change != null;
    }

    public boolean isApplied(){
        return applied;
    }

    protected void setApplied(){
        if (applied == false) {
            applied = true;
            if(change != null) change.setComplete();
        }
    }

    protected boolean isAcknowledgedBy(Change change){
        if (change == null) return false;
        // if we have a Change with the same change id from the same client id
        // then we were waiting for this change
        if(hasChangeId(change)){
            this.change = change;
            change.setAcknowledged();
            return true;
        }
        return false;
    }

    public boolean isError(){
        return errorCode != null;
    }

    public String getOperation(){
        return operation;
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
        return !isError() && (!hasSourceVersion() || sourceVersion.equals(0));
    }

    public String getKey(){
        return key;
    }

    public String getClientId(){
        return clientid;
    }

    public boolean hasSourceVersion(){
        return sourceVersion != null;
    }

    public Integer getSourceVersion(){
        if (sourceVersion == null) {
            return 0;
        }
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

    public List<String> getChangeIds(){
        return ccids;
    }

    public String toString(){
        if (!isError()) {
            return String.format("RemoteChange %s %s %s %d-%d", getKey(), getChangeVersion(), operation, getSourceVersion(), getObjectVersion());
        } else {
            return String.format("RemoteChange %s Error %d", getKey(), getErrorCode());
        }
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