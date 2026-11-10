package br.com.salonhub.dto.auth;

/** Define os dados devolvidos pela API no fluxo de autenticação, sem expor as entidades persistidas. */
public record InitialRegistrationResponse(
        Long establishmentId,
        String establishmentName,
        Long userId,
        String ownerName,
        String ownerEmail
) {
}
