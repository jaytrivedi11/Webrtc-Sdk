package co.in.vegg.webrtc_chat_sdk;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;

public class WebRtcClient {
    private static final String TAG = "WebRtcClient";

    private final Context context;
    private final PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private MediaStream mediaStream;
    private boolean isInitiator;
    private WebRtcClientListener listener;

    public interface WebRtcClientListener {
        void onLocalDescription(JSONObject sdp);
        void onIceCandidate(JSONObject candidate);
        void onCallConnected();
        void onCallDisconnected();
        void onCallError(String error);
    }

    public WebRtcClient(Context context) {
        this.context = context;

        // Initialize PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        // Create EglBase instance
        EglBase eglBase = EglBase.create();

        // Create PeerConnectionFactory
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

    public void setListener(WebRtcClientListener listener) {
        this.listener = listener;
    }

    public void initializePeerConnection(boolean isInitiator) {
        this.isInitiator = isInitiator;

        // Create audio constraints
        MediaConstraints audioConstraints = new MediaConstraints();

        // Create STUN/TURN servers for ICE
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        // Create peer connection
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
//        rtcConfig.enableDtlsSrtp = true;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    if (listener != null) {
                        listener.onCallConnected();
                    }
                } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    if (listener != null) {
                        listener.onCallDisconnected();
                    }
                }
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
                try {
                    JSONObject candidate = new JSONObject();
                    candidate.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                    candidate.put("sdpMid", iceCandidate.sdpMid);
                    candidate.put("candidate", iceCandidate.sdp);

                    if (listener != null) {
                        listener.onIceCandidate(candidate);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onAddTrack");
            }
        });

        // Create audio source and track
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource);
        localAudioTrack.setEnabled(true);

        // Create a local media stream and add the audio track
        mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS");
        mediaStream.addTrack(localAudioTrack);

        // Add the media stream to the peer connection
        peerConnection.addTrack(localAudioTrack);
    }

    public void createOffer() {
        if (peerConnection == null) {
            return;
        }

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Create offer success");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Set local description success");
                        try {
                            JSONObject messageJson = new JSONObject();
                            messageJson.put("type", "message");

                            JSONObject dataJson = new JSONObject();
                            dataJson.put("type", "offer");
                            dataJson.put("sdp", sessionDescription.description);

                            messageJson.put("data", dataJson);

                            if (listener != null) {
                                listener.onLocalDescription(messageJson);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCreateFailure(String s) {
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Set local description error: " + s);
                        if (listener != null) {
                            listener.onCallError("Set local description error: " + s);
                        }
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Create offer error: " + s);
                if (listener != null) {
                    listener.onCallError("Create offer error: " + s);
                }
            }

            @Override
            public void onSetFailure(String s) {
            }
        }, sdpConstraints);
    }

    public void onRemoteOfferReceived(JSONObject data) {
        try {
            String sdp = data.getString("sdp");
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, sdp);

            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "Set remote offer success");
                    createAnswer();
                }

                @Override
                public void onCreateFailure(String s) {
                }

                @Override
                public void onSetFailure(String s) {
                    Log.e(TAG, "Set remote offer error: " + s);
                    if (listener != null) {
                        listener.onCallError("Set remote offer error: " + s);
                    }
                }
            }, sessionDescription);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void createAnswer() {
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Create answer success");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Set local answer success");
                        try {
                            JSONObject messageJson = new JSONObject();
                            messageJson.put("type", "message");

                            JSONObject dataJson = new JSONObject();
                            dataJson.put("type", "answer");
                            dataJson.put("sdp", sessionDescription.description);

                            messageJson.put("data", dataJson);

                            if (listener != null) {
                                listener.onLocalDescription(messageJson);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCreateFailure(String s) {
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Set local answer error: " + s);
                        if (listener != null) {
                            listener.onCallError("Set local answer error: " + s);
                        }
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Create answer error: " + s);
                if (listener != null) {
                    listener.onCallError("Create answer error: " + s);
                }
            }

            @Override
            public void onSetFailure(String s) {
            }
        }, sdpConstraints);
    }

    public void onRemoteAnswerReceived(JSONObject data) {
        try {
            String sdp = data.getString("sdp");
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);

            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "Set remote answer success");
                }

                @Override
                public void onCreateFailure(String s) {
                }

                @Override
                public void onSetFailure(String s) {
                    Log.e(TAG, "Set remote answer error: " + s);
                    if (listener != null) {
                        listener.onCallError("Set remote answer error: " + s);
                    }
                }
            }, sessionDescription);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void onRemoteIceCandidateReceived(JSONObject candidateJson) {
        try {
            IceCandidate candidate = new IceCandidate(
                    candidateJson.getString("sdpMid"),
                    candidateJson.getInt("sdpMLineIndex"),
                    candidateJson.getString("candidate"));

            peerConnection.addIceCandidate(candidate);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setMicrophoneMute(boolean mute) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!mute);
        }
    }

    public void hangup() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
    }

    public void dispose() {
        hangup();

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
    }
}