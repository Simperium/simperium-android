package com.simperium.android;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.WebSocket;

import android.net.Uri;
import android.os.Handler;

import java.io.IOException;

class AsyncWebSocketProvider implements WebSocketManager.ConnectionProvider {

    public static final String TAG = "Simperium.AsyncWebSocketProvider";

    protected final String mAppId;
    protected final String mSessionId;
    protected final AsyncHttpClient mAsyncClient;
    protected final Handler mHandler = new Handler();

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

        // Protocol is null
        mAsyncClient.websocket(request, null, new WebSocketConnectCallback() {

            @Override
            public void onCompleted(Exception ex, final WebSocket webSocket) {
                if (ex != null) {
                    listener.onError(ex);
                    return;
                }

                if (webSocket == null) {
                    listener.onError(new IOException("WebSocket could not be opened"));
                    return;
                }

                final WebSocketManager.Connection connection = new WebSocketManager.Connection() {

                    @Override
                    public void close() {
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                webSocket.close();
                            }

                        });
                    }

                    @Override
                    public void send(final String message) {
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                webSocket.send(message);
                            }

                        });
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
