package co.in.vegg.webrtc_chat_sdk;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class AudioClient  implements SignalingClient.SignalingClientListener, WebRtcClient.WebRtcClientListener{
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
    };
    private SignalingClient signalingClient;
    private WebRtcClient webRtcClient;
    private boolean isJoined = false;
    private boolean isMuted = false;
    public final Context context;
    private static AudioClient instance;

    public AudioClient(Context context) {
        this.context = context;
    }
    public static synchronized AudioClient getInstance(Context context) {
        if (instance == null) {
            instance = new AudioClient(context);
        }
        return instance;
    }

    public static void start(Context context,Activity activity,String username){

        AudioClient audioClient = getInstance(context);
        audioClient.requestPermissions(context,activity);
        audioClient.joinRoom(context,username);
    }





    private void requestPermissions(Context context, Activity activity) {
        boolean allPermissionsGranted = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private void leaveRoom() {
        if (signalingClient != null) {
            signalingClient.leave();
            signalingClient.disconnect();
        }

        if (webRtcClient != null) {
            webRtcClient.dispose();
            webRtcClient = null;
        }
    }

    private void toggleMute() {
        if (webRtcClient != null) {
            isMuted = !isMuted;
            webRtcClient.setMicrophoneMute(isMuted);
        }
    }

    private void joinRoom(Context context,String username) {
        String roomId = "123456";

        if (roomId.isEmpty()) {
            Toast.makeText(context, "Server URL and Room ID are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (username.isEmpty()) {
            username = "User-" + System.currentTimeMillis() % 1000;
        }


        // Initialize WebRTC client
        webRtcClient = new WebRtcClient(context);
        webRtcClient.setListener(this);

        // Initialize Signaling client and connect
        signalingClient = new SignalingClient("ws://109.199.105.248:8484", this);
        signalingClient.createWebSocketClient();;

        // Join the room after connection is established
        signalingClient.join(roomId, username);
    }


    @Override
    public void onConnectionEstablished() {

    }

    @Override
    public void onConnectionError(String error) {

    }

    @Override
    public void onRoomJoined(String roomId, boolean isInitiator) {
        webRtcClient.initializePeerConnection(isInitiator);

    }

    @Override
    public void onPeerJoined() {
        webRtcClient.createOffer();

    }

    @Override
    public void onRoomInfo(int userCount) {

    }

    @Override
    public void onWebRtcMessage(JSONObject message) {
        try {
            String type = message.getString("type");
            JSONObject data = message.getJSONObject("data");

            switch (type) {
                case "message":
                    String messageType = data.getString("type");
                    switch (messageType) {
                        case "offer":
                            webRtcClient.onRemoteOfferReceived(data);
                            break;
                        case "answer":
                            webRtcClient.onRemoteAnswerReceived(data);
                            break;
                        case "candidate":
                            webRtcClient.onRemoteIceCandidateReceived(data.getJSONObject("candidate"));
                            break;
                    }
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPeerLeft() {
        webRtcClient.hangup();

    }

    @Override
    public void onClose() {

    }

    @Override
    public void onLocalDescription(JSONObject sdp) {
        signalingClient.sendMessage(sdp);

    }

    @Override
    public void onIceCandidate(JSONObject candidate) {
        signalingClient.sendCandidate(candidate);

    }

    @Override
    public void onCallConnected() {

    }

    @Override
    public void onCallDisconnected() {

    }

    @Override
    public void onCallError(String error) {

    }
}
