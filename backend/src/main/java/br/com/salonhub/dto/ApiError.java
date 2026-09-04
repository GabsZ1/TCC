package br.com.salonhub.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        int status,
        String message,
        Map<String, String> fields,
        OffsetDateTime timestamp
) {
}
