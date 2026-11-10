package br.com.salonhub.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica a aceitação e rejeição dos formatos de CNPJ. */
class CnpjValidatorTests {
    private final CnpjValidator validator = new CnpjValidator();

    @Test
    void acceptsOfficialAlphanumericExampleAndLegacyNumbers() {
        assertThat(validator.isValid("12.ABC.345/01DE-35", null)).isTrue();
        assertThat(validator.isValid("12abc34501de35", null)).isTrue();
        assertThat(validator.isValid("00.000.000/E08G-12", null)).isTrue();
        assertThat(validator.isValid("11.222.333/0001-81", null)).isTrue();
    }

    @Test
    void rejectsLettersInCheckDigitsAndMalformedOrIncorrectNumbers() {
        assertThat(validator.isValid("12.ABC.345/01DE-36", null)).isFalse();
        assertThat(validator.isValid("12ABC34501DE3A", null)).isFalse();
        assertThat(validator.isValid("12.ABC-345/01DE-35", null)).isFalse();
        assertThat(validator.isValid("AAAAAAAAAAAA00", null)).isFalse();
    }
}
