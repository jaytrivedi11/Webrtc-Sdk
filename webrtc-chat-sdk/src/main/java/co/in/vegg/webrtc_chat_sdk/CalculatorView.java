package co.in.vegg.webrtc_chat_sdk;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import co.in.vegg.webrtc_chat_sdk.interfaces.CalculationListener;


/**
 * Custom view that provides UI for the calculator functionality
 */
public class CalculatorView extends LinearLayout {

    private EditText etFirstNumber;
    private EditText etSecondNumber;
    private Button btnCalculate;
    private TextView tvResult;

    private int buttonColor = Color.BLUE;
    private String buttonText = "Add Numbers";

    private CalculationListener calculationListener;

    public CalculatorView(Context context) {
        super(context);
        init(context, null);
    }

    public CalculatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CalculatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Set orientation
        setOrientation(VERTICAL);

        // Parse custom attributes if available
        if (attrs != null) {
            // This would normally reference a custom style resource, but we'll omit that for this example
            // TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CalculatorView);
            // buttonColor = typedArray.getColor(R.styleable.CalculatorView_buttonColor, buttonColor);
            // buttonText = typedArray.getString(R.styleable.CalculatorView_buttonText);
            // typedArray.recycle();
        }

        // Inflate layout programmatically
        createViews(context);
        setupListeners();
    }

    private void createViews(Context context) {
        // First number input
        etFirstNumber = new EditText(context);
        etFirstNumber.setHint("Enter first number");
        etFirstNumber.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(etFirstNumber);

        // Second number input
        etSecondNumber = new EditText(context);
        etSecondNumber.setHint("Enter second number");
        etSecondNumber.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(etSecondNumber);

        // Calculate button
        btnCalculate = new Button(context);
        btnCalculate.setText(buttonText);
        btnCalculate.setBackgroundColor(buttonColor);
        btnCalculate.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(btnCalculate);

        // Result display
        tvResult = new TextView(context);
        tvResult.setText("Result: ");
        tvResult.setTextSize(18);
        tvResult.setPadding(0, 20, 0, 0);
        tvResult.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(tvResult);
    }

    private void setupListeners() {
        btnCalculate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                performCalculation();
            }
        });
    }

    /**
     * Performs the addition calculation using the input values
     */
    private void performCalculation() {
        String firstNumberStr = etFirstNumber.getText().toString();
        String secondNumberStr = etSecondNumber.getText().toString();

        // Validate inputs
        if (!Calculator.isValidNumber(firstNumberStr) || !Calculator.isValidNumber(secondNumberStr)) {
            String errorMessage = "Please enter valid numbers";
            tvResult.setText("Error: " + errorMessage);

            if (calculationListener != null) {
                calculationListener.onCalculationError(errorMessage);
            }
            return;
        }

        // Parse inputs
        double firstNumber = Double.parseDouble(firstNumberStr);
        double secondNumber = Double.parseDouble(secondNumberStr);

        // Perform calculation
        double result = Calculator.add(firstNumber, secondNumber);

        // Display result
        tvResult.setText("Result: " + result);

        // Notify listener
        if (calculationListener != null) {
            calculationListener.onCalculationComplete(firstNumber, secondNumber, result);
        }
    }

    /**
     * Sets the text for the calculate button
     *
     * @param text Button text
     */
    public void setButtonText(String text) {
        this.buttonText = text;
        if (btnCalculate != null) {
            btnCalculate.setText(text);
        }
    }

    /**
     * Sets the color for the calculate button
     *
     * @param color Button color (as a color int)
     */
    public void setButtonColor(int color) {
        this.buttonColor = color;
        if (btnCalculate != null) {
            btnCalculate.setBackgroundColor(color);
        }
    }

    /**
     * Sets the calculation listener
     *
     * @param listener The listener to be set
     */
    public void setCalculationListener(CalculationListener listener) {
        this.calculationListener = listener;
    }

    /**
     * Gets the first number from the input field
     *
     * @return The text in the first number field
     */
    public String getFirstNumber() {
        return etFirstNumber.getText().toString();
    }

    /**
     * Gets the second number from the input field
     *
     * @return The text in the second number field
     */
    public String getSecondNumber() {
        return etSecondNumber.getText().toString();
    }

    /**
     * Sets the first number in the input field
     *
     * @param number The number to set
     */
    public void setFirstNumber(double number) {
        etFirstNumber.setText(String.valueOf(number));
    }

    /**
     * Sets the second number in the input field
     *
     * @param number The number to set
     */
    public void setSecondNumber(double number) {
        etSecondNumber.setText(String.valueOf(number));
    }

    /**
     * Clears all input fields and results
     */
    public void clear() {
        etFirstNumber.setText("");
        etSecondNumber.setText("");
        tvResult.setText("Result: ");
    }
}