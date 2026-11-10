package br.com.salonhub.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;

/** Confere formato e dígitos verificadores de CNPJs numéricos ou alfanuméricos. */
public class CnpjValidator implements ConstraintValidator<ValidCnpj, String> {

    private static final int[] FIRST_DIGIT_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_DIGIT_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        boolean supportedFormat = normalizedValue.matches("[A-Z0-9]{12}[0-9]{2}")
                || normalizedValue.matches("[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-[0-9]{2}");
        if (!supportedFormat) {
            return false;
        }

        String cnpj = normalizedValue.replaceAll("[./-]", "");
        if (cnpj.substring(0, 12).chars().distinct().count() == 1) {
            return false;
        }

        int firstDigit = calculateDigit(cnpj.substring(0, 12), FIRST_DIGIT_WEIGHTS);
        int secondDigit = calculateDigit(cnpj.substring(0, 12) + firstDigit, SECOND_DIGIT_WEIGHTS);
        return cnpj.endsWith("" + firstDigit + secondDigit);
    }

    private int calculateDigit(String characters, int[] weights) {
        int sum = 0;
        for (int index = 0; index < weights.length; index++) {
            sum += (characters.charAt(index) - '0') * weights[index];
        }
        int digit = 11 - (sum % 11);
        return digit >= 10 ? 0 : digit;
    }
}
