package com.simperium.util;

import com.simperium.client.Channel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.Locale;

public class ChannelUtil {

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