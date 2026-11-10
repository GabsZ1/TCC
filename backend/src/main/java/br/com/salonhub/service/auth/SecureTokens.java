package br.com.salonhub.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Geração de tokens aleatórios para links enviados por e-mail e o hash guardado no banco. */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** 256 bits aleatórios em Base64 sem padding, seguro para usar em URL. */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Armazena apenas o resumo do token para não persistir o segredo enviado ao usuário. */
    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
