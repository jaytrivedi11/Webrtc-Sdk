package co.in.vegg.testing;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import co.in.vegg.webrtc_chat_sdk.VideoChatClient;

public class CallActivity extends AppCompatActivity {

    FrameLayout localview,remoteview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_call);
        localview = findViewById(R.id.localVideoView);
        remoteview = findViewById(R.id.remoteVideoView);

        VideoChatClient videoChatClient = VideoChatClient.getInstance(this);
        VideoChatClient.start(CallActivity.this,CallActivity.this,"user2");

        localview.addView(videoChatClient.getLocalVideoView());
        remoteview.addView(videoChatClient.getRemoteVideoView());
    }
}