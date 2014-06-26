package com.simperium.android;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.WebSocket;

import android.net.Uri;

class AsyncWebSocketProvider implements WebSocketManager.ConnectionProvider {

    protected final String mAppId;
    protected final String mSessionId;
    protected final AsyncHttpClient mAsyncClient;

    AsyncWebSocketProvider(String appId, String sessionId, AsyncHttpClient asyncClient) {
        mAppId = appId;
        mAsyncClient = asyncClient;
        mSessionId = sessionId;
    }

    @Override
    public void connect(final WebSocketManager.ConnectionListener listener) {

        Uri uri = Uri.parse(String.format(AndroidClient.WEBSOCKET_URL, mAppId));

        AsyncHttpRequest request = new AsyncHttpGet(uri);
        request.setHeader(AndroidClient.USER_AGENT_HEADER, mSessionId);

        // Protocl is null
        mAsyncClient.websocket(request, null, new WebSocketConnectCallback() {

            @Override
            public void onCompleted(Exception ex, final WebSocket webSocket) {
                if (ex != null) {
                    listener.onError(ex);
                }

                final WebSocketManager.Connection connection = new WebSocketManager.Connection() {

                    @Override
                    public void close() {
                        webSocket.close();
                    }

                    @Override
                    public void send(String message) {
                        webSocket.send(message);
                    }

                };

                webSocket.setStringCallback(new WebSocket.StringCallback() {

                   @Override
                   public void onStringAvailable(String s) {
                       listener.onMessage(s);
                   }

                });

                webSocket.setEndCallback(new CompletedCallback() {

                    @Override
                    public void onCompleted(Exception ex) {
                        listener.onDisconnect(ex);
                    }

                });

                listener.onConnect(connection);

            }

        });
    }

}
