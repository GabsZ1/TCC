package br.com.salonhub.dto.auth;

public record PasswordRecoveryResponse(String message, long expiresInMinutes) {
}
