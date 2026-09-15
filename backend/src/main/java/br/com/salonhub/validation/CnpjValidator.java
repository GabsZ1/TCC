package br.com.salonhub.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CnpjValidator implements ConstraintValidator<ValidCnpj, String> {

    private static final int[] FIRST_DIGIT_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_DIGIT_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String normalizedValue = value.trim();
        boolean supportedFormat = normalizedValue.matches("\\d{14}")
                || normalizedValue.matches("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
        if (!supportedFormat) {
            return false;
        }

        String digits = normalizedValue.replaceAll("\\D", "");
        if (digits.length() != 14 || digits.chars().distinct().count() == 1) {
            return false;
        }

        int firstDigit = calculateDigit(digits.substring(0, 12), FIRST_DIGIT_WEIGHTS);
        int secondDigit = calculateDigit(digits.substring(0, 12) + firstDigit, SECOND_DIGIT_WEIGHTS);
        return digits.endsWith("" + firstDigit + secondDigit);
    }

    private int calculateDigit(String digits, int[] weights) {
        int sum = 0;
        for (int index = 0; index < weights.length; index++) {
            sum += Character.digit(digits.charAt(index), 10) * weights[index];
        }
        int digit = 11 - (sum % 11);
        return digit >= 10 ? 0 : digit;
    }
}
