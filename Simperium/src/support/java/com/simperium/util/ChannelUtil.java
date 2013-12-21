package com.simperium.util;

import com.simperium.client.Channel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class ChannelUtil {

    public static void acknowledgeChange(Channel.MessageEvent event) {

        try {
            JSONArray responseJSON = RemoteChangesUtil.acknowledgeChange(event);
            event.channel.receiveMessage(String.format("c:%s", responseJSON));
        } catch (JSONException e) {
            throw new RuntimeException(String.format("Couldn't auto-acknowledge %s", event.message), e);
        }

    }

    public static void sendModifyOperation(Channel channel, String objectId) {

        

    }


}