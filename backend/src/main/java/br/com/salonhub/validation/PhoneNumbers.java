package br.com.salonhub.validation;

/** Telefones brasileiros: guardados só com dígitos (DDD + número, 10 ou 11 dígitos). */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** Remove máscara e espaços; devolve null se o resultado não for um telefone válido. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            digits = digits.substring(2);
        }
        return digits.matches("[1-9][0-9]{9,10}") ? digits : null;
    }

    /** Aceita apenas dígitos e caracteres usuais de formatação de telefone. */
    public static boolean hasOnlyPhoneFormattingCharacters(String value) {
        return value != null && value.matches("[0-9+()\\s-]+");
    }
}
