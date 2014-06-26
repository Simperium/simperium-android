package com.simperium.test;

import com.simperium.android.WebSocketManager;
import com.simperium.android.WebSocketManager.Connection;
import com.simperium.android.WebSocketManager.ConnectionListener;
import com.simperium.android.WebSocketManager.ConnectionProvider;

import java.util.ArrayList;
import java.util.List;

public class MockConnection implements Connection {

    public ConnectionListener listener = new NullListener();
    public Boolean closed = false;
    public String lastMessage;
    public List<String> messages = new ArrayList<String>();

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void send(String message) {
        lastMessage = message;
        messages.add(message);
    }

    public ConnectionProvider buildProvider() {
        return new ConnectionProvider() {
            @Override
            public Connection connect(ConnectionListener connectionListener, String url, String userAgent) {
                MockConnection.this.listener = connectionListener;
                return MockConnection.this;
            }            
        };
    }


    private class NullListener implements ConnectionListener {

        @Override
        public void onConnect() {
            // noop
        }

        @Override
        public void onMessage(String message) {
            // noop
        }

        @Override
        public void onDisconnect() {
            // noop
        }

    }

}