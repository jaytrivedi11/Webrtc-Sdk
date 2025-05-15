package co.in.vegg.webrtc_chat_sdk.interfaces;

public interface CalculationListener {
    /**
     * Called when a calculation is performed successfully
     *
     * @param firstNumber First number used in calculation
     * @param secondNumber Second number used in calculation
     * @param result Result of the calculation
     */
    void onCalculationComplete(double firstNumber, double secondNumber, double result);

    /**
     * Called when there is an error during calculation
     *
     * @param errorMessage Description of the error
     */
    void onCalculationError(String errorMessage);
}