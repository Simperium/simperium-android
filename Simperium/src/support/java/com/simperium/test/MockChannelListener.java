package com.simperium.test;

import com.simperium.client.Channel;
import com.simperium.util.Uuid;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockChannelListener implements Channel.OnMessageListener {

    static public final String TAG = "SimperiumTest";

    public boolean open = false, autoAcknowledge = false, initReceived = false;
    public String api = null;
    public List<Channel.MessageEvent> messages = Collections.synchronizedList(new ArrayList<Channel.MessageEvent>());
    public List<String> logs = new ArrayList<String>();
    public Channel.MessageEvent lastMessage;
    public Map<String,String> indexData = new HashMap<String,String>();
    public JSONArray indexVersions;

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

        Message message = parseMessage(event.message);

        if (message.isCommand(Channel.COMMAND_INIT)) {
            // init already received
            if (initReceived) throw new RuntimeException("Channel sent init after already sending init");
            initReceived = true;

            try {
                JSONObject initParams = new JSONObject(message.payload);
                api = initParams.getString(Channel.FIELD_API_VERSION);
            } catch (JSONException e) {
                throw new RuntimeException("Invalid init params", e);
            }

        }

        if (message.isCommand(Channel.COMMAND_ENTITY)) {
            if (indexData.containsKey(message.payload)) {
                Channel channel = (Channel) event.getSource();
                channel.receiveMessage(String.format("e:%s\n%s", message.payload, indexData.get(message.payload)));;
            }
        }

        if (autoAcknowledge == true && message.isCommand(Channel.COMMAND_CHANGE)) {
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

        lastMessage = event;
    }

    @Override
    public void onLog(Channel channel, int level, CharSequence message) {
        logs.add(message.toString());
    }

    @Override
    public void onClose(Channel channel) {
        open = false;
    }

    @Override
    public void onOpen(Channel channel) {
        open = true;
    }

    static public final String COLON = ":";
    public static Message parseMessage(String message) {
        int colon = message.indexOf(COLON);
        String command = null, payload = null;
        if (colon == -1) {
            command = message;
            payload = "";
        } else {
            command = message.substring(0, colon);
            payload = message.substring(colon+1);
        }

        return new Message(command, payload);
    }

    public static class Message {

        final public String command;
        final public String payload;

        public Message(String command, String payload) {
            this.command = command;
            this.payload = payload;
        }

        public boolean isCommand(String command) {
            return this.command.equals(command);
        }
    }

}