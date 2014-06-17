package com.simperium.test;

import com.codebutler.android_websockets.WebSocketClient;

import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class MockWebSocketClient extends WebSocketClient {

    public List<String> messages = new ArrayList<String>(5);
    public String lastMessage = null;
    public boolean connected = false;

    public MockWebSocketClient(URI uri, Listener listener, List<BasicNameValuePair> extraHeaders) {
        super(uri, listener, extraHeaders);
    }

    @Override
    public void connect() {
        connected = true;
        getListener().onConnect();
    }

    @Override
    public void disconnect() {
        connected = false;
        getListener().onDisconnect(0, "EOF");
    }

    @Override
    public void send(String data) {
        messages.add(data);
        lastMessage = data;
    }

    @Override
    public void send(byte[] data) {
        // not used with Simperium currently
    }

}