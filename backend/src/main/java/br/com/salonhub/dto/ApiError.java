package br.com.salonhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        int status,
        @JsonProperty("mensagem") String message,
        @JsonProperty("campos") Map<String, String> fields,
        OffsetDateTime timestamp
) {
}
