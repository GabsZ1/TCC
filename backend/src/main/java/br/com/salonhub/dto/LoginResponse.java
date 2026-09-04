package br.com.salonhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record LoginResponse(
        String token,
        @JsonProperty("tipo") String type,
        @JsonProperty("expiraEm") Instant expiresAt,
        @JsonProperty("usuario") AuthenticatedUserResponse user
) {
}
