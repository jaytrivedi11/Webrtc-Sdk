package co.in.vegg.webrtc_chat_sdk;

public class Calculator {

    /**
     * Adds two numbers together
     *
     * @param a First number
     * @param b Second number
     * @return The sum of the two numbers
     */
    public static double add(double a, double b) {
        return a + b;
    }

    /**
     * Validates if the input string can be parsed as a number
     *
     * @param input The string to check
     * @return true if the string can be parsed as a number, false otherwise
     */
    public static boolean isValidNumber(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        try {
            Double.parseDouble(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}