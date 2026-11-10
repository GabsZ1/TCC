package br.com.salonhub.dto.auth;

/** Define os dados devolvidos pela API no fluxo de autenticação, sem expor as entidades persistidas. */
public record PasswordRecoveryResponse(String message, long expiresInMinutes) {
}
