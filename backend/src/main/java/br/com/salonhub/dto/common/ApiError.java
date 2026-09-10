package br.com.salonhub.dto.common;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        int status,
        String message,
        Map<String, String> fields,
        OffsetDateTime timestamp
) {
}
