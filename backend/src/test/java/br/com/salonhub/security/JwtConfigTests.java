package br.com.salonhub.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Verifica a configuração usada para emitir e validar tokens JWT. */
class JwtConfigTests {

    private final JwtConfig jwtConfig = new JwtConfig();

    @Test
    void mustRejectBlankSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtConfig.jwtSecretKey("  "))
                .withMessageContaining("JWT_SECRET e obrigatorio");
    }

    @Test
    void mustRejectInvalidBase64Secret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtConfig.jwtSecretKey("not-base64!"))
                .withMessage("JWT_SECRET deve ser um valor Base64 valido");
    }

    @Test
    void mustRejectSecretShorterThan256Bits() {
        String shortSecret = Base64.getEncoder().encodeToString("short-secret".getBytes(StandardCharsets.UTF_8));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtConfig.jwtSecretKey(shortSecret))
                .withMessage("JWT_SECRET deve possuir ao menos 256 bits em Base64");
    }

    @Test
    void mustCreateHs256KeyFromValidBase64Secret() {
        String secret = Base64.getEncoder().encodeToString(
                "local-test-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));

        SecretKey key = jwtConfig.jwtSecretKey(secret);

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSizeGreaterThanOrEqualTo(32);
    }
}
