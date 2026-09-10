package br.com.salonhub.dto.auth;

public record InitialRegistrationResponse(
        Long establishmentId,
        String establishmentName,
        Long userId,
        String ownerName,
        String ownerEmail
) {
}
