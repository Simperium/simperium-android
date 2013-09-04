package com.simperium.testapp.mock;

import com.simperium.client.Channel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.simperium.util.Uuid;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class MockChannelListener implements Channel.OnMessageListener {

    public boolean open = false, autoAcknowledge;
    public List<Channel.MessageEvent> messages = Collections.synchronizedList(new ArrayList<Channel.MessageEvent>());
    public Channel.MessageEvent lastMessage;

    public void sendEmptyIndex(Channel channel){
        channel.receiveMessage("i:{\"index\":[]}");
    }

    public void clearMessages(){
        messages.clear();
        lastMessage = null;
    }

    @Override
    public void onMessage(Channel.MessageEvent event) {
        messages.add(event);
        lastMessage = event;

        if (autoAcknowledge == true && event.message.indexOf("c:") == 0) {
            try {
                Channel channel = (Channel) event.getSource();
                JSONObject changeJSON = new JSONObject(event.message.substring(2));
                JSONObject ackJSON = new JSONObject();
                JSONArray ccids = new JSONArray();
                ccids.put(changeJSON.get("ccid"));
                ackJSON.put("clientid", channel.getSessionId());
                ackJSON.put("id", changeJSON.getString("id"));
                ackJSON.put("o", changeJSON.getString("o"));
                ackJSON.put("v", changeJSON.getJSONObject("v"));
                ackJSON.put("ccids", ccids);
                ackJSON.put("cv", Uuid.uuid());
                int sv = -1;
                if (changeJSON.has("sv")) {
                    sv = changeJSON.getInt("sv");
                    ackJSON.put("sv", changeJSON.getInt("sv"));
                }
                ackJSON.put("ev", sv + 1);

                JSONArray responseJSON = new JSONArray();
                responseJSON.put(ackJSON);
                channel.receiveMessage(String.format("c:%s", responseJSON));
            } catch (JSONException e) {
                throw new RuntimeException(String.format("Couldn't auto-acknowledge %s", event.message), e);
            }
        }
    }

    @Override
    public void onClose(Channel channel) {
        open = false;
    }

    @Override
    public void onOpen(Channel channel) {
        open = true;
    }

}