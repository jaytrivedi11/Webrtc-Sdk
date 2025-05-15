package co.in.vegg.webrtc_chat_sdk;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


public class SignalingClient {
    private static final String TAG = "SignalingClient";

    private final String serverUrl;
    private WebSocket webSocketClient;
    private final SignalingClientListener listener;
    private String roomId;
    private String username;
    private final Handler handler;
    private final OkHttpClient client = new OkHttpClient();

    public interface SignalingClientListener {
        void onConnectionEstablished();
        void onConnectionError(String error);
        void onRoomJoined(String roomId, boolean isInitiator);
        void onPeerJoined();
        void onRoomInfo(int userCount);
        void onWebRtcMessage(JSONObject message);
        void onPeerLeft();
        void onClose();
    }

    public SignalingClient(String serverUrl, SignalingClientListener listener) {
        this.serverUrl = serverUrl;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
    }



    public void createWebSocketClient() {


        Request request = new Request.Builder().url(serverUrl).build();

        webSocketClient = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                Log.d(TAG, "WebSocket connection opened");
                handler.post(() -> listener.onConnectionEstablished());

                // Join room after connection is established

            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                Log.d(TAG, "WebSocket message received: " + text);
                try {
                    JSONObject jsonMessage = new JSONObject(text);
                    handleSignalingMessage(jsonMessage);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing WebSocket message: " + e.getMessage());
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
                Log.d(TAG, "WebSocket closing: " + reason);
                Log.d(TAG, "WebSocket connection closed");
                handler.post(() -> listener.onClose());
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                Log.e(TAG, "WebSocket error: " + t.getMessage());

                Log.e(TAG, "WebSocket exception: " + t.getMessage());
                handler.post(() -> listener.onConnectionError(t.getMessage()));
            }
        });
    }

    private void handleSignalingMessage(JSONObject jsonMessage) {
        try {
            String type = jsonMessage.getString("type");

            switch (type) {
                case "created":
                    roomId = jsonMessage.getString("room");
                    handler.post(() -> listener.onRoomJoined(roomId, true));
                    break;

                case "joined":
                    roomId = jsonMessage.getString("room");
                    handler.post(() -> listener.onRoomJoined(roomId, false));
                    break;

                case "ready":
                    handler.post(() -> listener.onPeerJoined());
                    break;

                case "roomInfo":
                    int userCount = jsonMessage.getInt("userCount");
                    handler.post(() -> listener.onRoomInfo(userCount));
                    break;

                case "message":
                case "candidate":
                    handler.post(() -> listener.onWebRtcMessage(jsonMessage));
                    break;

                case "bye":
                    handler.post(() -> listener.onPeerLeft());
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error handling signaling message: " + e.getMessage());
        }
    }

    public void join(String roomId, String username) {
        this.roomId = roomId;
        this.username = username;

        try {
            JSONObject joinMessage = new JSONObject();
            joinMessage.put("type", "join");
            joinMessage.put("room", roomId);
            joinMessage.put("username", username);

            webSocketClient.send(joinMessage.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating join message: " + e.getMessage());
        }
    }

    public void sendMessage(JSONObject message) {
        webSocketClient.send(message.toString());
    }

    public void sendCandidate(JSONObject candidate) {
        try {
            JSONObject candidateMessage = new JSONObject();
            candidateMessage.put("type", "candidate");
            candidateMessage.put("candidate", candidate);

            webSocketClient.send(candidateMessage.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating candidate message: " + e.getMessage());
        }
    }

    public void leave() {
        if (webSocketClient != null && roomId != null) {
            try {
                JSONObject byeMessage = new JSONObject();
                byeMessage.put("type", "bye");
                byeMessage.put("room", roomId);

                webSocketClient.send(byeMessage.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Error creating bye message: " + e.getMessage());
            }
        }
    }

    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close(1000, "App closing");
            webSocketClient = null;
        }
    }
}
