package com.simperium.test;

import com.simperium.android.WebSocketManager;
import com.simperium.android.WebSocketManager.Connection;
import com.simperium.android.WebSocketManager.ConnectionListener;
import com.simperium.android.WebSocketManager.ConnectionProvider;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class MockConnection implements Connection {

    public static final String TAG = "MockConnection";

    public ConnectionListener listener = new NullListener();
    public Boolean closed = false;
    public String lastMessage;
    public List<String> messages = new ArrayList<String>();

    public void receiveMessage(String message) {
        listener.onMessage(message);
    }

    public void clearMessages() {
        lastMessage = null;
        messages.clear();
    }

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
            public void connect(ConnectionListener connectionListener) {
                MockConnection.this.listener = connectionListener;
                connectionListener.onConnect(MockConnection.this);
            }            
        };
    }


    private class NullListener implements ConnectionListener {

        @Override
        public void onConnect(Connection connection) {
            // noop
            Log.d(TAG, "NullConnection.onConnect " + connection);
        }

        @Override
        public void onMessage(String message) {
            // noop
            Log.d(TAG, "NullConnection.onMessage " + message);
        }

        @Override
        public void onError(Exception exception) {
            // noop
            Log.d(TAG, "NullConnection.onError");
        }

        @Override
        public void onDisconnect(Exception error) {
            // noop
            Log.d(TAG, "NullConnection.onError");
        }

    }

}