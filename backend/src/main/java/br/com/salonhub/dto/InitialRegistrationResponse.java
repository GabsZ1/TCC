package br.com.salonhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InitialRegistrationResponse(
        @JsonProperty("estabelecimentoId") Long establishmentId,
        @JsonProperty("nomeEstabelecimento") String establishmentName,
        @JsonProperty("usuarioId") Long userId,
        @JsonProperty("nomeResponsavel") String ownerName,
        @JsonProperty("emailResponsavel") String ownerEmail
) {
}
