package co.in.vegg.testing;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import co.in.vegg.webrtc_chat_sdk.ChatClient;
import co.in.vegg.webrtc_chat_sdk.interfaces.MessageCallback;


public class MainActivity extends AppCompatActivity {

//    private CalculatorView calculatorView;
    private Button btn, send;
    private EditText message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn = findViewById(R.id.btnJoin);
        message = findViewById(R.id.etMessage);
        send = findViewById(R.id.btnSend);
        ChatClient chatClient = ChatClient.getInstance(this);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatClient.start(MainActivity.this, "user1");
            }
        });

        chatClient.setMessageCallback(new MessageCallback() {
            @Override
            public void onMessageReceived(String message) {
                Log.e("Message",message);
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chatClient.sendMessage(message.getText().toString());
            }
        });


        // Get the calculator view from the layout
//        calculatorView = findViewById(R.id.calculator_view);
//
//        // Customize the calculator
//        calculatorView.setButtonText("Calculate Sum");
//        calculatorView.setButtonColor(getResources().getColor(android.R.color.holo_green_dark));
//
//        // Set calculation listener
//        calculatorView.setCalculationListener(new CalculationListener() {
//            @Override
//            public void onCalculationComplete(double firstNumber, double secondNumber, double result) {
//                Toast.makeText(MainActivity.this,
//                        "Calculation complete: " + firstNumber + " + " + secondNumber + " = " + result,
//                        Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onCalculationError(String errorMessage) {
//                Toast.makeText(MainActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
//            }
//        });
    }
}