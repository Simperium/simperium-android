package com.simperium.util;

import com.simperium.client.Change;
import com.simperium.client.Channel;
import com.simperium.client.RemoteChange;

import com.simperium.util.JSONDiff;
import com.simperium.util.Uuid;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class RemoteChangesUtil {

    public static JSONObject operation(String id, String client, String cv, String ccid)
    throws JSONException {

        JSONObject change = new JSONObject();
        change.put(RemoteChange.CHANGE_IDS_KEY, new JSONArray("[\"" + ccid + "\"]"));
        change.put(RemoteChange.CLIENT_KEY, client);
        change.put(RemoteChange.CHANGE_VERSION_KEY, cv);
        change.put(RemoteChange.ID_KEY, id);

        return change;
    }

    public static JSONObject operation(String id, String client, String changeId)
    throws JSONException {
        return operation(id, client, Uuid.uuid(), changeId);
    }

    public static JSONObject operation(String id, String client)
    throws JSONException {
        return operation(id, client, Uuid.uuid());
    }

    public static JSONObject operation(String id)
    throws JSONException {
        return operation(id, "mock-client");
    }

    /**
     * Generate a delete operation for the given object id with a random ccid
     * and cv.
     */
    public static JSONObject deleteOperation(String id)
    throws JSONException {
        
        JSONObject change = operation(id);
        change.put(RemoteChange.OPERATION_KEY, RemoteChange.OPERATION_REMOVE);

        return change;

    }

    public static JSONObject addOperation(String id)
    throws JSONException {
        JSONObject change = operation(id);
        change.put(RemoteChange.OPERATION_KEY, RemoteChange.OPERATION_MODIFY);
        return change;
    }

    public static JSONObject modifyOperation(String id, int sourceVersion, JSONObject diff)
    throws JSONException {
        JSONObject change = addOperation(id);
        change.put(RemoteChange.SOURCE_VERSION_KEY, sourceVersion);
        change.put(RemoteChange.END_VERSION_KEY, sourceVersion + 1);
        change.put(JSONDiff.DIFF_VALUE_KEY, diff);

        return change;
    }

    public static JSONArray acknowledgeChange(Channel.MessageEvent channelEvent)
    throws JSONException {
        JSONObject change = new JSONObject(channelEvent.message.substring(2));
        return acknowledgeChange(change, channelEvent.channel);
    }

    public static JSONArray acknowledgeChange(JSONObject change, Channel channel)
    throws JSONException {

        String id = change.getString(Change.ID_KEY);
        String client = channel.getSessionId();
        String ccid = change.getString(Change.CHANGE_ID_KEY);

        JSONObject ack = operation(id, client, ccid);
        ack.put(RemoteChange.OPERATION_KEY, change.getString(Change.OPERATION_KEY));
        ack.put(JSONDiff.DIFF_VALUE_KEY, change.getJSONObject(JSONDiff.DIFF_VALUE_KEY));

        int sv = -1;

        if (change.has(RemoteChange.SOURCE_VERSION_KEY)) {
            sv = change.getInt(RemoteChange.SOURCE_VERSION_KEY);
            ack.put(RemoteChange.SOURCE_VERSION_KEY, change.getInt(RemoteChange.SOURCE_VERSION_KEY));
        }

        ack.put(RemoteChange.END_VERSION_KEY, sv + 1);

        JSONArray response = new JSONArray();
        response.put(ack);

        return response;
    }

}