package com.simperium.android;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket transport backed by OkHttp, replacing {@link AsyncWebSocketProvider} (AndroidAsync).
 *
 * AndroidAsync is unmaintained (last release ~2022) and the source of intermittent transport
 * crashes (e.g. the "double connect callback" AssertionError during the SSL handshake). OkHttp is
 * actively maintained, already shipped by most host apps, and delivers each text message fully
 * reassembled to {@link WebSocketListener#onMessage(WebSocket, String)}.
 *
 * OkHttp's WebSocket is thread-safe — send()/close() enqueue internally — so, unlike the
 * AndroidAsync provider, we don't hop onto the main looper. Listener callbacks arrive on OkHttp's
 * dispatcher thread, which is what {@link WebSocketManager} already expects.
 */
class OkHttpWebSocketProvider implements WebSocketManager.ConnectionProvider {

    // Normal closure (RFC 6455).
    private static final int CLOSE_NORMAL = 1000;

    private final OkHttpClient mClient;
    private final String mAppId;
    private final String mSessionId;

    OkHttpWebSocketProvider(String appId, String sessionId, OkHttpClient client) {
        mAppId = appId;
        mSessionId = sessionId;
        mClient = client;
    }

    @Override
    public void connect(final WebSocketManager.ConnectionListener listener) {
        try {
            String url = String.format(AndroidClient.WEBSOCKET_URL, mAppId);
            Request request = new Request.Builder()
                .url(url)
                // OkHttp rejects header values with non-printable / non-ASCII characters (where
                // AndroidAsync did not). The session id is sent as User-Agent, so sanitize it.
                .header(AndroidClient.USER_AGENT_HEADER, sanitizeHeaderValue(mSessionId))
                .build();

            // Whether the upgrade succeeded — used to route onFailure correctly (see below).
            final AtomicBoolean opened = new AtomicBoolean(false);

            mClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                    opened.set(true);
                    listener.onConnect(new WebSocketManager.Connection() {
                        @Override
                        public void close() {
                            webSocket.close(CLOSE_NORMAL, null);
                        }

                        @Override
                        public void send(String message) {
                            webSocket.send(message);
                        }
                    });
                }

                @Override
                public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                    listener.onMessage(text);
                }

                @Override
                public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                    webSocket.close(CLOSE_NORMAL, null);
                }

                @Override
                public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                    listener.onDisconnect(null);
                }

                @Override
                public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                    Exception ex = t instanceof Exception ? (Exception) t : new IOException(t);
                    // After a successful upgrade, onFailure is OkHttp's terminal disconnect (no
                    // onClosed follows). Route it through onDisconnect so the manager notifies
                    // channels, cancels the heartbeat, and schedules a reconnect — matching the
                    // AndroidAsync end/closed-callback behavior. A pre-open failure is a genuine
                    // connect error.
                    if (opened.get()) {
                        listener.onDisconnect(ex);
                    } else {
                        listener.onError(ex);
                    }
                }
            });
        } catch (Throwable t) {
            listener.onError(t instanceof Exception ? (Exception) t : new IOException(t));
        }
    }

    /** Strip characters OkHttp won't allow in a header value (keep printable ASCII + tab). */
    private static String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || (c >= ' ' && c <= '~')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
