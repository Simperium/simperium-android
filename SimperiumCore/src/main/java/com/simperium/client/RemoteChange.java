package com.simperium.client;

import com.simperium.util.JSONDiff;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Encapsulates parsing and logic for remote changes
 */
public class RemoteChange {

    /**
     * Possible simperium response errors
     * @see https://gist.github.com/beaucollins/6998802#error-responses
     */
    public enum ResponseCode {
        OK               (200),
        INVALID_ID       (400),
        UNAUTHORIZED     (401),
        NOT_FOUND        (404),
        INVALID_VERSION  (405),
        DUPLICATE_CHANGE (409),
        EMPTY_CHANGE     (412),
        EXCEEDS_MAX_SIZE (413),
        INVALID_DIFF     (440);

        public final int code;

        ResponseCode(int code) {
            this.code = code;
        }

    }

    static public final String TAG = "Simperium.RemoteChange";

    public static final String ID_KEY             = "id";
    public static final String CLIENT_KEY         = "clientid";
    public static final String ERROR_KEY          = "error";
    public static final String END_VERSION_KEY    = "ev";
    public static final String SOURCE_VERSION_KEY = "sv";
    public static final String CHANGE_VERSION_KEY = "cv";
    public static final String CHANGE_IDS_KEY     = "ccids";
    public static final String OPERATION_KEY      = JSONDiff.DIFF_OPERATION_KEY;
    public static final String VALUE_KEY          = JSONDiff.DIFF_VALUE_KEY;
    public static final String OPERATION_MODIFY   = "M";
    public static final String OPERATION_REMOVE   = JSONDiff.OPERATION_REMOVE;

    private String key;
    private String clientid;
    private JSONArray ccids;
    private Integer sourceVersion;
    private Integer entityVersion;
    private String changeVersion;
    private String operation;
    private JSONObject value;
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
    public RemoteChange(String clientid, String key, JSONArray ccids, String changeVersion,
        Integer sourceVersion, Integer entityVersion, String operation, JSONObject value) {
        this.clientid = clientid;
        this.key = key;
        this.ccids = ccids;
        this.sourceVersion = sourceVersion;
        this.entityVersion = entityVersion;
        this.operation = operation;
        this.value = value;
        this.changeVersion = changeVersion;
    }

    public RemoteChange(String clientid, String key, JSONArray ccids, String changeVersion,
        Integer sourceVersion, Integer entityVersion, JSONObject diff)
        throws JSONException {
        this(clientid, key, ccids, changeVersion, sourceVersion, entityVersion,
            diff.getString(JSONDiff.DIFF_OPERATION_KEY),
            diff.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
    }

    public RemoteChange(String clientid, String key, JSONArray ccids, Integer errorCode){
        this.clientid = clientid;
        this.key = key;
        this.ccids = ccids;
        this.errorCode = errorCode;
    }

    protected Ghost apply(Ghost ghost) throws RemoteChangeInvalidException {
        // keys and versions must match otherwise throw an error
        if (!ghost.getSimperiumKey().equals(getKey())) {
            throw(new RemoteChangeInvalidException(this,
                    String.format(Locale.US, "Local instance key %s does not match change key %s",
                        ghost.getSimperiumKey(), getKey())));
        }

        if (isModifyOperation() && !ghost.getVersion().equals(getSourceVersion())) {
            throw(new RemoteChangeInvalidException(this,
                    String.format(Locale.US, "Local instance of %s has source version of %d and remote change has %d",
                    getKey(), ghost.getVersion(), getSourceVersion())));
        }

        if (isAddOperation() && ghost.getVersion() != 0) {
            throw(new RemoteChangeInvalidException(this, "Local instance has version greater than 0 with remote add operation"));
        }

        try {
            JSONObject properties = jsondiff.apply(ghost.getDiffableValue(), getPatch());
            return new Ghost(getKey(), getObjectVersion(), properties);
        } catch (JSONException e) {
            throw new RemoteChangeInvalidException(this, String.format("Unable to apply patch: %s", getPatch()), e);
        } catch (IllegalArgumentException e) {
            throw new RemoteChangeInvalidException(this, "Invalid patch", e);
        }

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

    public boolean isAcknowledgedBy(Change change){
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
        if (operation == null) return false;
        return operation.equals(OPERATION_REMOVE);
    }

    public boolean isModifyOperation(){
        if (operation == null) return false;
        return operation.equals(OPERATION_MODIFY) && sourceVersion != null && sourceVersion > 0;
    }

    public boolean isAddOperation(){
        if (operation == null) return false;
        return operation.equals(OPERATION_MODIFY) && (sourceVersion == null || sourceVersion <= 0);
    }

    public ResponseCode getResponseCode() {
        return responseForCode(getErrorCode());
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

    public JSONObject getPatch(){
        return value;
    }

    public boolean hasChangeId(String ccid){

        if (ccid == null) return false;

        int length = ccids.length();
        for (int i=0; i<length; i++) {
            try {
                if (ccid.equals(ccids.getString(i)))
                    return true;;
            } catch (JSONException e) {
                // invalid CCID
            }
        }

        return false;
    }

    public boolean hasChangeId(Change change){
        return hasChangeId(change.getChangeId());
    }

    public JSONArray getChangeIds(){
        return ccids;
    }

    public String toString(){
        if (!isError()) {
            return String.format(Locale.US, "RemoteChange %s %s %s %d-%d", getKey(), getChangeVersion(), operation, getSourceVersion(), getObjectVersion());
        } else {
            return String.format(Locale.US, "RemoteChange %s Error %d", getKey(), getErrorCode());
        }
    }

    public static RemoteChange buildFromMap(JSONObject changeData)
    throws JSONException {
        // get the list of ccids that this applies to
        JSONArray ccids = changeData.getJSONArray(CHANGE_IDS_KEY);
        // get the client id
        String client_id = changeData.getString(CLIENT_KEY);
        // get the id of the object it applies to
        String id = changeData.getString(ID_KEY);
        if (changeData.has(ERROR_KEY)) {
            int errorCode = changeData.getInt(ERROR_KEY);
            return new RemoteChange(client_id, id, ccids, errorCode);
        }

        String operation = changeData.getString(OPERATION_KEY);
        Integer sourceVersion = changeData.optInt(SOURCE_VERSION_KEY);
        Integer objectVersion = changeData.optInt(END_VERSION_KEY);
        JSONObject patch = changeData.optJSONObject(VALUE_KEY);
        String changeVersion = changeData.getString(CHANGE_VERSION_KEY);

        return new RemoteChange(client_id, id, ccids, changeVersion, sourceVersion, objectVersion, operation, patch);

    }

    public static ResponseCode responseForCode(Integer code) {

        if (code == null)
            return ResponseCode.OK;

        for (ResponseCode responseCode : ResponseCode.values()) {
            if (responseCode.code == code) {
                return responseCode;
            }
        }

        return ResponseCode.OK;
    }

}
