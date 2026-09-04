package br.com.salonhub.dto;

public record InitialRegistrationResponse(
        Long establishmentId,
        String establishmentName,
        Long userId,
        String ownerName,
        String ownerEmail
) {
}
