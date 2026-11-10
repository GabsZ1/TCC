package br.com.salonhub.dto.auth;

import java.time.Instant;

/** Define os dados devolvidos pela API no fluxo de autenticação, sem expor as entidades persistidas. */
public record LoginResponse(
        String token,
        String type,
        Instant expiresAt,
        AuthenticatedUserResponse user
) {
}
