package co.in.vegg.webrtc_chat_sdk;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.in.vegg.webrtc_chat_sdk.interfaces.MessageCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class ChatClient {
    private static final String TAG = "ChatClient";
    private static ChatClient instance;

    private static final String SERVER_URL = "ws://109.199.105.248:8484";
    private List<ChatMessage> messagesList = new ArrayList<>();
    private WebSocket webSocket;
    private final OkHttpClient client = new OkHttpClient();
    private static PeerConnectionFactory peerConnectionFactory;
    private Map<String, PeerConnection> peerConnections = new HashMap<>();
    private Map<String, DataChannel> dataChannels = new HashMap<>();
    DataChannel dataChannel1;
    private String username;
    private String roomId;
    private boolean isInitiator = false;
    private MessageCallback messageCallback;
    public final Context context;

    public ChatClient(Context context) {
        this.context = context;
    }

    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    public static synchronized ChatClient getInstance(Context context) {
        if (instance == null) {
            instance = new ChatClient(context);
        }
        return instance;
    }

    public static void start(Context context,String username) {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        ChatClient chatClient = getInstance(context);
        chatClient.joinChat(username);

    }
    private void sendJoinMessage() {
        try {
            JSONObject joinMsg = new JSONObject();
            joinMsg.put("type", "join");
            joinMsg.put("room", roomId);
            joinMsg.put("username", username);

            webSocket.send(joinMsg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating join message", e);
        }

    }
    private void joinChat(String user_name){
         username = user_name;
         roomId = "12345678";
        Request request = new Request.Builder().url(SERVER_URL).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                Log.d(TAG, "WebSocket connection established");

                // Join room after connection is established
                sendJoinMessage();


            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                Log.d(TAG, "Message received: " + text);
                handleSignalingMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
                Log.d(TAG, "WebSocket closing: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                Log.e(TAG, "WebSocket error: " + t.getMessage());


            }
        });

    }

    private void handleSignalingMessage(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            String type = jsonMessage.getString("type");

            switch (type) {
                case "created":
                    handleRoomCreated();
                    break;
                case "joined":
                    handleRoomJoined();
                    break;
                case "ready":
                    handlePeerReady();
                    break;
                case "message":
                    handleDataMessage(jsonMessage);
                    break;
                case "roomInfo":
                    handleRoomInfo(jsonMessage);
                    break;
                case "bye":
                    handlePeerDisconnected(jsonMessage);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing message", e);
        }
    }


    private void handleRoomCreated() {
        isInitiator = true;

//        runOnUiThread(() -> {
//            tvStatus.setText("Room created. Waiting for others to join.");
//            btnJoin.setEnabled(false);
//            btnSend.setEnabled(true);
//
//            // Add system message
//            addMessage(new ChatMessage("System", "You created room: " + roomId, true));
//        });
    }

    private void handleRoomJoined() {
        isInitiator = false;

//        runOnUiThread(() -> {
//            tvStatus.setText("Joined room: " + roomId);
//            btnJoin.setEnabled(false);
//            btnSend.setEnabled(true);
//
//            // Add system message
//            addMessage(new ChatMessage("System", "You joined room: " + roomId, true));
//        });
    }

    private void handlePeerReady() {
        // This is sent to initiator when another peer joins
        if (isInitiator) {
//            runOnUiThread(() -> {
//                addMessage(new ChatMessage("System", "Another user joined the room", true));
//            });

            // As initiator, we'll create an offer for the peer
            createPeerConnection("remote-peer"); // In a real app, you'd use the actual peer ID
        }
    }

    private void createPeerConnection(String peerId) {
        Log.d(TAG, "Creating peer connection for: " + peerId);

        // ICE servers (STUN/TURN)
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        // Create peer connection
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new PeerConnection.Observer() {
                    @Override
                    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                        Log.d(TAG, "onSignalingChange: " + signalingState);
                    }

                    @Override
                    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                        Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                    }

                    @Override
                    public void onIceConnectionReceivingChange(boolean b) {
                        Log.d(TAG, "onIceConnectionReceivingChange: " + b);
                    }

                    @Override
                    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                        Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
                    }

                    @Override
                    public void onIceCandidate(IceCandidate iceCandidate) {
                        Log.d(TAG, "onIceCandidate: " + iceCandidate);
                        sendIceCandidate(iceCandidate);
                    }

                    @Override
                    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                        Log.d(TAG, "onIceCandidatesRemoved");
                    }

                    @Override
                    public void onAddStream(MediaStream mediaStream) {
                        Log.d(TAG, "onAddStream");
                    }

                    @Override
                    public void onRemoveStream(MediaStream mediaStream) {
                        Log.d(TAG, "onRemoveStream");
                    }

                    @Override
                    public void onDataChannel(DataChannel dataChannel) {
                        Log.d(TAG, "onDataChannel");
                        setupDataChannel(dataChannel, peerId);
                    }

                    @Override
                    public void onRenegotiationNeeded() {
                        Log.d(TAG, "onRenegotiationNeeded");
                        if (isInitiator) {
                            createOffer(peerId);
                        }
                    }

                    @Override
                    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                        Log.d(TAG, "onAddTrack");
                    }
                });

        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection");
            return;
        }

        peerConnections.put(peerId, peerConnection);

        // Create data channel if initiator
        if (isInitiator) {
            createDataChannel(peerConnection, peerId);
        }
    }

    private void createDataChannel(PeerConnection peerConnection, String peerId) {
        DataChannel.Init init = new DataChannel.Init();
        init.ordered = true;

        DataChannel dataChannel = peerConnection.createDataChannel("chat", init);
        setupDataChannel(dataChannel, peerId);
    }

    private void setupDataChannel(DataChannel dataChannel, String peerId) {
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {
                Log.d(TAG, "Data channel buffered amount changed: " + l);
            }

            @Override
            public void onStateChange() {
                Log.d(TAG, "Data channel state changed: " + dataChannel.state());

//                runOnUiThread(() -> {
//                    if (dataChannel.state() == DataChannel.State.OPEN) {
//                        tvStatus.setText("Connected to peer: " + peerId);
//                    } else if (dataChannel.state() == DataChannel.State.CLOSED) {
//                        tvStatus.setText("Disconnected from peer: " + peerId);
//                    }
//                });
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                ByteBuffer data = buffer.data;
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                final String message = new String(bytes, StandardCharsets.UTF_8);

                Log.d(TAG, "Received message via data channel: " + message);

                try {
                    JSONObject jsonMessage = new JSONObject(message);
                    final String sender = jsonMessage.getString("username");
                    final String text = jsonMessage.getString("text");
                    if (messageCallback != null) {
                        messageCallback.onMessageReceived(text);
                    }
//                    runOnUiThread(() -> {
//                        addMessage(new ChatMessage(sender, text, false));
//                    });
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing data channel message", e);
                }
            }
        });

        dataChannels.put(peerId, dataChannel);
        Log.e(TAG,"-----------------------------");
        Log.e(TAG,dataChannels.values().toString());

    }

    private void createOffer(String peerId) {
        PeerConnection peerConnection = peerConnections.get(peerId);
        if (peerConnection == null) return;

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Offer created");

                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set");

                        // Send offer to remote peer through signaling server
                        sendSessionDescription(sessionDescription);
                    }
                }, sessionDescription);
            }
        }, constraints);
    }

    private void sendSessionDescription(SessionDescription sessionDescription) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "message");

            JSONObject data = new JSONObject();
            data.put("type", sessionDescription.type.canonicalForm());
            data.put("sdp", sessionDescription.description);

            message.put("data", data);

            webSocket.send(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending session description", e);
        }
    }

    private void sendIceCandidate(IceCandidate candidate) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "candidate");

            JSONObject candidateJson = new JSONObject();
            candidateJson.put("sdpMLineIndex", candidate.sdpMLineIndex);
            candidateJson.put("sdpMid", candidate.sdpMid);
            candidateJson.put("candidate", candidate.sdp);

            message.put("candidate", candidateJson);

            webSocket.send(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending ICE candidate", e);
        }
    }

    private void handleDataMessage(JSONObject jsonMessage) throws JSONException {
        if (!jsonMessage.has("data")) return;

        JSONObject data = jsonMessage.getJSONObject("data");
        String dataType = data.getString("type");
        String from = jsonMessage.optString("from", "Unknown");

        switch (dataType) {
            case "offer":
                handleOfferReceived(data, from);
                break;
            case "answer":
                handleAnswerReceived(data);
                break;
            case "candidate":
                handleCandidateReceived(data);
                break;
            case "text":
                // Plain text message from signaling server
                String text = data.getString("text");
                if (messageCallback != null) {
                    messageCallback.onMessageReceived(text);
                }
//                runOnUiThread(() -> {
//                    addMessage(new ChatMessage(from, text, false));
//                });
                break;
        }
    }

    private void handleOfferReceived(JSONObject data, String peerId) throws JSONException {
        Log.d(TAG, "Received offer from: " + peerId);

        if (!peerConnections.containsKey(peerId)) {
            createPeerConnection(peerId);
        }

        PeerConnection peerConnection = peerConnections.get(peerId);
        if (peerConnection == null) return;

        String sdp = data.getString("sdp");
        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.OFFER, sdp);

        peerConnection.setRemoteDescription(new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote description set after offer");
                createAnswer(peerId);
            }
        }, sessionDescription);
    }

    private void createAnswer(String peerId) {
        PeerConnection peerConnection = peerConnections.get(peerId);
        if (peerConnection == null) return;

        MediaConstraints constraints = new MediaConstraints();

        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Answer created");

                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set for answer");
                        sendSessionDescription(sessionDescription);
                    }
                }, sessionDescription);
            }
        }, constraints);
    }

    private void handleAnswerReceived(JSONObject data) throws JSONException {
        Log.d(TAG, "Received answer");

        String sdp = data.getString("sdp");
        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.ANSWER, sdp);

        // For simplicity, using the first peer connection
        // In a real app, you'd match by peer ID
        for (PeerConnection peerConnection : peerConnections.values()) {
            peerConnection.setRemoteDescription(new SimpleSdpObserver() {
                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "Remote description set after answer");
                }
            }, sessionDescription);
            break;
        }
    }

    private void handleCandidateReceived(JSONObject data) throws JSONException {
        JSONObject candidateJson = data.getJSONObject("candidate");

        IceCandidate candidate = new IceCandidate(
                candidateJson.getString("sdpMid"),
                candidateJson.getInt("sdpMLineIndex"),
                candidateJson.getString("candidate")
        );

        // Add candidate to all peer connections for simplicity
        // In a real app, you'd match by peer ID
        for (PeerConnection peerConnection : peerConnections.values()) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    private void handleRoomInfo(JSONObject jsonMessage) throws JSONException {
        int userCount = jsonMessage.getInt("userCount");

//        runOnUiThread(() -> {
//            tvStatus.setText("Room: " + roomId + " - Users: " + userCount);
//
//            // If there are users but no peer connections, we might need to establish them
            if (userCount > 1 && peerConnections.isEmpty() && isInitiator) {
                createPeerConnection("remote-peer");
            }
//        });
    }

    private void handlePeerDisconnected(JSONObject jsonMessage) throws JSONException {
        String peerId = jsonMessage.getString("id");
        String username = jsonMessage.optString("username", "Unknown");

        // Clean up resources
        if (peerConnections.containsKey(peerId)) {
            PeerConnection peerConnection = peerConnections.remove(peerId);
            peerConnection.close();
        }

        if (dataChannels.containsKey(peerId)) {
            DataChannel dataChannel = dataChannels.remove(peerId);
            dataChannel.close();
        }

//        runOnUiThread(() -> {
//            addMessage(new ChatMessage("System", username + " left the room", true));
//        });
    }

    public void sendMessage(String message) {

        if (message.isEmpty()) return;

        try {
            // Create message object
            JSONObject chatMessage = new JSONObject();
            chatMessage.put("username", username);
            chatMessage.put("text", message);

            // Add to local display
            addMessage(new ChatMessage(username, message, true));

            // Send through WebRTC data channels if available
            boolean sentThroughDataChannel = false;
            for (DataChannel dataChannel : dataChannels.values()) {

                if (dataChannel.state() == DataChannel.State.OPEN) {
                    Log.e(TAG,"opeennnnnnnnn")
;                    ByteBuffer buffer = ByteBuffer.wrap(chatMessage.toString().getBytes(StandardCharsets.UTF_8));
                    dataChannel.send(new DataChannel.Buffer(buffer, false));
                    sentThroughDataChannel = true;
                }
            }
            Log.e(TAG,"-----------------------------");
            Log.e(TAG,dataChannels.values().toString());

            if(dataChannels.values() == null){
                Log.e(TAG, "nulll");
            }



//


            // Clear input field
        } catch (JSONException e) {
            Log.e(TAG, "Error sending message", e);
        }
    }

    private void addMessage(ChatMessage message) {
        messagesList.add(message);
    }

}
