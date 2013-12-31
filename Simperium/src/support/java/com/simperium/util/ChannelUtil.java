package com.simperium.util;

import com.simperium.client.Syncable;

import com.simperium.client.Channel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.Locale;

public class ChannelUtil {

    public static JSONObject parseChangeData(Channel.MessageEvent event)
    throws JSONException {
        if (event.message.substring(0,2).equals("c:")) {
            JSONObject change = new JSONObject(event.message.substring(2));
            return change;
        }

        throw new RuntimeException("Not a change message: " + event.message);
    }

    /**
     * Send a remote change message to the channel that ransforms Syncable origin
     * to contain target representation.
     * 
     * Generates a random `cv` and increments the `sv` by one.
     */
    public static void sendRemoteChange(Channel channel, Syncable origin, JSONObject target) {
        try {
            String id = origin.getSimperiumKey();
            int version = origin.getVersion();
            JSONObject diff = JSONDiff.diff(origin.getDiffableValue(), target);
            JSONObject modify = RemoteChangesUtil.modifyOperation(id, version, diff.getJSONObject("v"));

            channel.receiveMessage("c:[" + modify.toString() + "]");
        } catch (JSONException e) {
            throw new RuntimeException("Unable to send remote change", e);
        }
    }

    public static void replyWithError(Channel.MessageEvent event, Integer error) {

        try {
            JSONObject change = parseChangeData(event);
            JSONObject reply = new JSONObject();
            reply.put("ccids", new JSONArray("[\"" + change.getString("ccid") + "\"]"));
            reply.put("id", change.getString("id"));
            reply.put("clientid", event.channel.getSessionId());
            reply.put("error", error);

            event.channel.receiveMessage("c:[" + reply.toString() + "]");

        } catch (JSONException e) {
            throw new RuntimeException(String.format("Couldn't reply with error %s", event.message), e);
        }

    }

    public static void acknowledgeChange(Channel.MessageEvent event) {

        try {
            JSONArray responseJSON = RemoteChangesUtil.acknowledgeChange(event);
            event.channel.receiveMessage(String.format("c:%s", responseJSON));
        } catch (JSONException e) {
            throw new RuntimeException(String.format("Couldn't auto-acknowledge %s", event.message), e);
        }

    }

    public static void sendModifyOperation(Channel channel, String objectId, int sourceVersion, JSONObject diff) {

        try {
            JSONObject modify = RemoteChangesUtil.modifyOperation(objectId, sourceVersion, diff);
            channel.receiveMessage(String.format("c:[%s]", modify));
        } catch (JSONException e) {
            throw new RuntimeException("Could not build change", e);
        }

    }

    public static void sendObject(Channel channel, String id, int version, JSONObject data) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("data", data);
            channel.receiveMessage(String.format(Locale.US, "e:%s.%d\n%s", id, version, payload));
        } catch (JSONException e) {
            throw new RuntimeException("Could not send object data", e);
        }
    }

}