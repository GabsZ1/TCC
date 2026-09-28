package br.com.salonhub.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Documentos válidos e únicos para os testes (o CPF de funcionário é único no banco).
 * A sequência parte de um valor ligado ao relógio para não colidir com dados que
 * testes anteriores tenham deixado gravados.
 */
public final class TestDocuments {

    private static final AtomicLong CPF_SEQUENCE =
            new AtomicLong(100_000_000L + Math.floorMod(System.nanoTime(), 800_000_000L));

    private TestDocuments() {
    }

    /** CPF com dígitos verificadores corretos, só números. */
    public static String cpf() {
        while (true) {
            long next = CPF_SEQUENCE.getAndIncrement();
            if (next > 999_999_999L) {
                CPF_SEQUENCE.set(100_000_000L);
                continue;
            }
            String base = String.format("%09d", next);
            if (base.chars().distinct().count() == 1) {
                continue;
            }
            int first = checkDigit(base);
            return base + first + checkDigit(base + first);
        }
    }

    private static int checkDigit(String digits) {
        int sum = 0;
        for (int index = 0; index < digits.length(); index++) {
            sum += (digits.charAt(index) - '0') * (digits.length() + 1 - index);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
