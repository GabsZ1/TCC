package br.com.salonhub.dto;

import java.time.Instant;

public record LoginResponse(
        String token,
        String type,
        Instant expiresAt,
        AuthenticatedUserResponse user
) {
}
