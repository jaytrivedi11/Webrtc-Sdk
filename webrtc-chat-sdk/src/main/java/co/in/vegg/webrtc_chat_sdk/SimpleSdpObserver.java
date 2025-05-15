package co.in.vegg.webrtc_chat_sdk;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class SimpleSdpObserver implements SdpObserver {
    private static final String TAG = "SimpleSdpObserver";

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(TAG, "SDP created successfully");
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "SDP set successfully");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.e(TAG, "SDP creation failed: " + s);
    }

    @Override
    public void onSetFailure(String s) {
        Log.e(TAG, "SDP setting failed: " + s);
    }
}
