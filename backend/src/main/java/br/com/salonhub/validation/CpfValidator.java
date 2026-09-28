package br.com.salonhub.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Aceita "00000000000" ou "000.000.000-00" e confere os dígitos verificadores. */
public class CpfValidator implements ConstraintValidator<ValidCpf, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("\\d{11}") && !trimmed.matches("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}")) {
            return false;
        }
        return isValidDigits(normalize(trimmed));
    }

    /** Só os dígitos. */
    public static String normalize(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    static boolean isValidDigits(String digits) {
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }
        return digits.charAt(9) - '0' == checkDigit(digits, 9) && digits.charAt(10) - '0' == checkDigit(digits, 10);
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += (digits.charAt(index) - '0') * (length + 1 - index);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
