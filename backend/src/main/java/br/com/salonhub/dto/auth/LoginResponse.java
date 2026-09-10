package br.com.salonhub.dto.auth;

import java.time.Instant;

public record LoginResponse(
        String token,
        String type,
        Instant expiresAt,
        AuthenticatedUserResponse user
) {
}
