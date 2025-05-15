package co.in.vegg.webrtc_chat_sdk;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class VideoChatClient {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
    };
    private static VideoChatClient instance;
    public final Context context;
    private static final String TAG = "VideoCallActivity";
    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final int VIDEO_RESOLUTION_WIDTH = 1920;
    private static final int VIDEO_RESOLUTION_HEIGHT = 1080;
    private static final int FPS = 30;
    private String roomId;
    private String username;
    private AudioManager audioManager;

    private boolean isInitiator = false;
    private boolean isChannelReady = false;
    private boolean isStarted = false;

    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private CameraVideoCapturer videoCapturer;
    private TextView tvRoomInfo;
    private Button btnToggleCamera, btnMute, btnEndCall;
    private boolean isMuted = false;
    private boolean isFrontCamera = true;
    private WebSocket webSocket;
    private List<RtpSender> senders = new ArrayList<>();

    // Change signaling server URL to your actual server address with the correct port
    private static final String SIGNALING_SERVER_URL = "ws://109.199.105.248:8484";  // For emulator use localhost
    // Use "ws://YOUR_SERVER_IP:3000" for real devices


    public VideoChatClient(Context context) {
        this.context = context;
        localVideoView = new SurfaceViewRenderer(context);
        remoteVideoView = new SurfaceViewRenderer(context);
    }
    public static synchronized VideoChatClient getInstance(Context context) {
        if (instance == null) {
            instance = new VideoChatClient(context);
        }
        return instance;
    }
    public static void start(Context context,Activity activity,String username){

        VideoChatClient videoChatClient = getInstance(context);
        if (videoChatClient.checkPermissions(context)) {
            videoChatClient.setup(username);
        } else {
            videoChatClient.requestPermissions(context,activity);
        }
    }
    private boolean checkPermissions(Context context) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    private void requestPermissions(Context context, Activity activity) {
        ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }
    private void setup(String user){
        username = user;
        roomId = "5678";
        eglBase = EglBase.create();
        VideoChatClient videoChatClient = getInstance(context);

        // Initialize video renderers
        localVideoView.init(eglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.init(eglBase.getEglBaseContext(), null);
        initializePeerConnectionFactory(context);
        createVideoTrackFromCamera(context);
        connectToSignallingServer();
    }


    private void connectToSignallingServer() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(SIGNALING_SERVER_URL).build();
        Log.d(TAG, "Connecting to signaling server at " + SIGNALING_SERVER_URL);

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                Log.d(TAG, "WebSocket connected");
             //   runOnUiThread(() -> Toast.makeText(CallActivity.this, "Connected to signaling server", Toast.LENGTH_SHORT).show());

                // Join room
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "join");
                    message.put("room", roomId);
                    message.put("username", username);
                    webSocket.send(message.toString());
                    Log.d(TAG, "Sent join message: " + message.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received message: " + text);
                handleSignallingData(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
//                runOnUiThread(() -> Toast.makeText(CallActivity.this, "Connection error: " + t.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
            }
        });
    }

    private void handleSignallingData(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            String type = json.getString("type");
            Log.d(TAG, "Signal type: " + type);

            switch (type) {
                case "created":
                    Log.d(TAG, "Created room");
                    isInitiator = true;
                    break;
                case "joined":
                    Log.d(TAG, "Joined room");
                    isChannelReady = true;
                    break;
                case "ready":
                    Log.d(TAG, "Ready to start call");
                    // When both parties are ready, the initiator starts the call
                    if (isInitiator && !isStarted) {
//                        runOnUiThread(() -> startCall());
                        startCall();
                    }
                    break;
                case "message":
                    if (json.has("data")) {
                        JSONObject data = json.getJSONObject("data");
                        String messageType = data.getString("type");

                        switch (messageType) {
                            case "offer":
                                Log.d(TAG, "Received offer");
                                if (!isStarted) {
//                                    runOnUiThread(() -> startCall());
                                    startCall();
                                }
//                                runOnUiThread(() -> {
//                                    try {
//                                        String sdp = data.getString("sdp");
//                                        peerConnection.setRemoteDescription(
//                                                new SimpleSdpObserver(),
//                                                new SessionDescription(SessionDescription.Type.OFFER, sdp)
//                                        );
//                                        createAnswer();
//                                    } catch (JSONException e) {
//                                        e.printStackTrace();
//                                    }
//                                });
                                try {
                                    String sdp = data.getString("sdp");
                                    peerConnection.setRemoteDescription(
                                            new SimpleSdpObserver(),
                                            new SessionDescription(SessionDescription.Type.OFFER, sdp)
                                    );
                                    createAnswer();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                break;

                            case "answer":
                                Log.d(TAG, "Received answer");
                                if (isStarted) {
                                    try {
                                        String sdp = data.getString("sdp");
                                        peerConnection.setRemoteDescription(
                                                new SimpleSdpObserver(),
                                                new SessionDescription(SessionDescription.Type.ANSWER, sdp)
                                        );
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
//                                    runOnUiThread(() -> {
//                                        try {
//                                            String sdp = data.getString("sdp");
//                                            peerConnection.setRemoteDescription(
//                                                    new SimpleSdpObserver(),
//                                                    new SessionDescription(SessionDescription.Type.ANSWER, sdp)
//                                            );
//                                        } catch (JSONException e) {
//                                            e.printStackTrace();
//                                        }
//                                    });
                                }
                                break;

                            case "candidate":
                                Log.d(TAG, "Received ICE candidate");
                                if (isStarted) {
//                                    runOnUiThread(() -> {
//                                        try {
//                                            JSONObject candidate = data.getJSONObject("candidate");
//                                            String sdpMid = candidate.getString("sdpMid");
//                                            int sdpMLineIndex = candidate.getInt("sdpMLineIndex");
//                                            String sdp = candidate.getString("candidate");
//
//                                            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
//                                            peerConnection.addIceCandidate(iceCandidate);
//                                        } catch (JSONException e) {
//                                            e.printStackTrace();
//                                        }
//                                    });
                                    try {
                                        JSONObject candidate = data.getJSONObject("candidate");
                                        String sdpMid = candidate.getString("sdpMid");
                                        int sdpMLineIndex = candidate.getInt("sdpMLineIndex");
                                        String sdp = candidate.getString("candidate");

                                        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                                        peerConnection.addIceCandidate(iceCandidate);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                                break;
                        }
                    }
                    break;

                case "roomInfo":
                    // Update room information if needed
                    break;

                case "bye":
                    Log.d(TAG, "Remote user left");
//                  this::handleRemoteHangup);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializePeerConnectionFactory(Context context) {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();

        PeerConnectionFactory.initialize(initializationOptions);

        // Create peer connection factory
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory =
                new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory =
                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
    }

    private void createVideoTrackFromCamera(Context context) {
        // Create video capturer
        videoCapturer = createVideoCapturer(context);
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create video capturer");
            return;
        }

        // Create video source
        SurfaceTextureHelper surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);

        // Create local video track
        localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(localVideoView);

        // Create audio source and track
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.optional.add(new MediaConstraints.KeyValuePair("googCpuOveruseDetection", "false"));
        audioConstraints.optional.add(new MediaConstraints.KeyValuePair("googHighBitrate", "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));

        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

        localAudioTrack.setEnabled(true);
    }
    public View getLocalVideoView() {
        return localVideoView;
    }

    public View getRemoteVideoView() {
        return remoteVideoView;
    }
    private CameraVideoCapturer createVideoCapturer(Context context) {
        CameraVideoCapturer videoCapturer;
        if (Camera2Enumerator.isSupported(context)) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(context));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private CameraVideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front-facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front-facing camera not found, try other cameras
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void startCall() {
        if (!isStarted && (isChannelReady || isInitiator)) {
            Log.d(TAG, "Starting call as " + (isInitiator ? "initiator" : "answerer"));
            isStarted = true;
            createPeerConnection();
            setPreferredCodec();

            // Add tracks to the peer connection
            addTracksToLocalPeerConnection();

            // If we're the initiator, create and send an offer
            if (isInitiator) {
                createOffer();
            }
        }
    }

    private void addTracksToLocalPeerConnection() {
        if (peerConnection == null || localVideoTrack == null || localAudioTrack == null) {
            Log.e(TAG, "Cannot add tracks to PeerConnection, it's null");
            return;
        }

        // Add video track
        RtpSender videoSender = peerConnection.addTrack(localVideoTrack);
        RtpParameters parameters = videoSender.getParameters();
        for (RtpParameters.Encoding encoding : parameters.encodings) {
            encoding.maxBitrateBps = 2_000_000; // 2 Mbps
            encoding.maxFramerate = 30;
        }
        videoSender.setParameters(parameters);
        senders.add(videoSender);

        // Add audio track
        RtpSender audioSender = peerConnection.addTrack(localAudioTrack);
        senders.add(audioSender);

        Log.d(TAG, "Added local audio and video tracks to peer connection");
    }

    private void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();

        // Add STUN server
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer());
        // Add TURN server with credentials
        iceServers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;

        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED; // Prefer UDP for lower latency
        rtcConfig.enableCpuOveruseDetection = true;  // Add this to improve performance

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                Log.d(TAG, "onIceCandidate: " + iceCandidate);

                try {
                    // Create candidate object
                    JSONObject candidateJson = new JSONObject();
                    candidateJson.put("sdpMid", iceCandidate.sdpMid);
                    candidateJson.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                    candidateJson.put("candidate", iceCandidate.sdp);

                    // Create candidate message
                    JSONObject candidateMessage = new JSONObject();
                    candidateMessage.put("type", "candidate");
                    candidateMessage.put("candidate", candidateJson);

                    // Wrap in data object to match server's expectations
                    JSONObject message = new JSONObject();
                    message.put("type", "message");
                    message.put("data", candidateMessage);

                    // Send to signaling server
                    if (webSocket != null && webSocket.send(message.toString())) {
                        Log.d(TAG, "Sent ICE candidate: " + message);
                    } else {
                        Log.e(TAG, "Failed to send ICE candidate");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                super.onTrack(transceiver);
                Log.d(TAG, "onTrack: " + transceiver.getMid());

                if (transceiver.getReceiver().track() instanceof VideoTrack) {
                    VideoTrack remoteVideoTrack = (VideoTrack) transceiver.getReceiver().track();
                    remoteVideoTrack.setEnabled(true);
                    remoteVideoTrack.addSink(remoteVideoView);
                    Log.d(TAG, "Added remote video track to renderer");
//                    runOnUiThread(() -> {
//                        remoteVideoTrack.setEnabled(true);
//                        remoteVideoTrack.addSink(remoteVideoView);
//                        Log.d(TAG, "Added remote video track to renderer");
//                    });
                }
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                super.onIceConnectionChange(iceConnectionState);
                Log.d(TAG, "Ice Connection State: " + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
                        iceConnectionState == PeerConnection.IceConnectionState.COMPLETED) {
//                    Toast.makeText(CallActivity.this, "Connected to peer", Toast.LENGTH_SHORT).show();
                } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED ||
                        iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
//                    Toast.makeText(CallActivity.this, "ICE connection failed or disconnected", Toast.LENGTH_SHORT).show();
                }
//                runOnUiThread(() -> {
//                    if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
//                            iceConnectionState == PeerConnection.IceConnectionState.COMPLETED) {
//                        Toast.makeText(CallActivity.this, "Connected to peer", Toast.LENGTH_SHORT).show();
//                    } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED ||
//                            iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
//                        Toast.makeText(CallActivity.this, "ICE connection failed or disconnected", Toast.LENGTH_SHORT).show();
//                    }
//                });
            }
        });

        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection");
        }
    }
    private void setPreferredCodec() {
        if (peerConnection == null) return;

        // Find the video transceiver to modify encoding parameters
        for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
            if (transceiver.getMediaType() == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                RtpParameters parameters = transceiver.getSender().getParameters();
                if (parameters != null && parameters.encodings != null && !parameters.encodings.isEmpty()) {
                    for (RtpParameters.Encoding encoding : parameters.encodings) {
                        // Increase bitrate for better quality
                        encoding.maxBitrateBps = 2500000; // 2.5 Mbps
                        encoding.minBitrateBps = 1000000; // 1 Mbps
                        encoding.maxFramerate = 30;
                    }
                    transceiver.getSender().setParameters(parameters);
                }
                break;
            }
        }
    }
    private void createOffer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is null when trying to create offer");
            return;
        }

        Log.d(TAG, "Creating offer...");
        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        // Add this to both createOffer and createAnswer methods
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("maxFrameRate", "30"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("minFrameRate", "24"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Create offer success");

                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Set local description success");

                        // Format message according to server's expected format
                        try {
                            // Create offer object
                            JSONObject offerJson = new JSONObject();
                            offerJson.put("type", "offer");
                            offerJson.put("sdp", sessionDescription.description);

                            // Wrap in data object to match server's expectations
                            JSONObject message = new JSONObject();
                            message.put("type", "message");
                            message.put("data", offerJson);

                            // Send to signaling server
                            if (webSocket != null && webSocket.send(message.toString())) {
                                Log.d(TAG, "Sent offer: " + message);
                            } else {
                                Log.e(TAG, "Failed to send offer");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, sessionDescription);
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Create offer failure: " + s);
//                runOnUiThread(() -> Toast.makeText(CallActivity.this, "Failed to create offer", Toast.LENGTH_SHORT).show());
            }
        }, sdpMediaConstraints);
    }

    private void createAnswer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is null when trying to create answer");
            return;
        }

        Log.d(TAG, "Creating answer...");
        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Create answer success");

                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Set local description success for answer");

                        // Format message according to server's expected format
                        try {
                            // Create answer object
                            JSONObject answerJson = new JSONObject();
                            answerJson.put("type", "answer");
                            answerJson.put("sdp", sessionDescription.description);

                            // Wrap in data object to match server's expectations
                            JSONObject message = new JSONObject();
                            message.put("type", "message");
                            message.put("data", answerJson);

                            // Send to signaling server
                            if (webSocket != null && webSocket.send(message.toString())) {
                                Log.d(TAG, "Sent answer: " + message);
                            } else {
                                Log.e(TAG, "Failed to send answer");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, sessionDescription);
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Create answer failure: " + s);
//                runOnUiThread(() -> Toast.makeText(CallActivity.this, "Failed to create answer", Toast.LENGTH_SHORT).show());
            }
        }, sdpMediaConstraints);
    }

    private void toggleCamera() {
        if (videoCapturer != null) {
            videoCapturer.switchCamera(null);
            isFrontCamera = !isFrontCamera;
//            Toast.makeText(this, "Switched to " + (isFrontCamera ? "front" : "back") + " camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleMute() {
        if (localAudioTrack != null) {
            isMuted = !isMuted;
            localAudioTrack.setEnabled(!isMuted);
            btnMute.setText(isMuted ? "Unmute" : "Mute");
//            Toast.makeText(this, isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
        }
    }

    private void endCall() {
        // Send bye message to signaling server
        if (webSocket != null) {
            JSONObject message = new JSONObject();
            try {
                message.put("type", "bye");
                message.put("room", roomId);
                message.put("username", username);
                webSocket.send(message.toString());
                Log.d(TAG, "Sent bye message");
                webSocket.close(1000, "User ended call");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Close local components
        closeResources();

        // Finish activity
//        finish();
    }

    private void handleRemoteHangup() {
//        Toast.makeText(CallActivity.this, "Remote peer hung up", Toast.LENGTH_LONG).show();

        // Reset call state
        isStarted = false;
        isChannelReady = false;

        // Clean up senders list
        senders.clear();

        // Close PeerConnection but don't end the whole call session
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        // Clear remote view
        if (remoteVideoView != null) {
            remoteVideoView.clearImage();
        }
    }

    private void closeResources() {
        isStarted = false;
        isChannelReady = false;
        senders.clear();

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (localVideoView != null) {
            localVideoView.release();
        }

        if (remoteVideoView != null) {
            remoteVideoView.release();
        }

        if (webSocket != null) {
            webSocket.close(1000, "Activity destroyed");
            webSocket = null;
        }
    }


    private String preferCodec(String sdp, String codec, boolean isOffer) {
        String[] lines = sdp.split("\r\n");
        int mLineIndex = -1;
        String codecRtpMap = null;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("m=video")) {
                mLineIndex = i;
            }
            if (lines[i].contains("a=rtpmap") && lines[i].toLowerCase().contains(codec.toLowerCase())) {
                codecRtpMap = lines[i].split(" ")[0].split(":")[1];
            }
        }

        if (mLineIndex == -1 || codecRtpMap == null) return sdp;

        String[] parts = lines[mLineIndex].split(" ");
        StringBuilder newMLine = new StringBuilder();
        int partIndex = 0;
        newMLine.append(parts[partIndex++]); // m=
        newMLine.append(" ").append(parts[partIndex++]); // video
        newMLine.append(" ").append(parts[partIndex++]); // port
        newMLine.append(" ").append(parts[partIndex++]); // RTP/AVP or UDP/TLS/RTP/SAVPF

        newMLine.append(" ").append(codecRtpMap);
        for (; partIndex < parts.length; partIndex++) {
            if (!parts[partIndex].equals(codecRtpMap)) {
                newMLine.append(" ").append(parts[partIndex]);
            }
        }

        lines[mLineIndex] = newMLine.toString();
        return String.join("\r\n", lines);
    }

    // Simple SDP observer implementation
    private class SimpleSdpObserver implements org.webrtc.SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.d(TAG, "SDP onCreateSuccess");
        }

        @Override
        public void onSetSuccess() {
            Log.d(TAG, "SDP onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.e(TAG, "SDP onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            Log.e(TAG, "SDP onSetFailure: " + s);
        }
    }

    // Peer connection observer implementation
    private class PeerConnectionObserver implements PeerConnection.Observer {
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
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(TAG, "onIceCandidatesRemoved: " + iceCandidates.length);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream: " + mediaStream.getId());
            // Note: This method is deprecated and will not be called with Unified Plan
            // Use onTrack instead
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream: " + mediaStream.getId());
            // Note: This method is deprecated and will not be called with Unified Plan
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "onDataChannel: " + dataChannel.label());
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "onAddTrack");
            // Note: This method is also deprecated in newer WebRTC versions
            // Use onTrack (with RtpTransceiver) instead
        }
    }
}
