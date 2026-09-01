package br.com.salonhub.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        int status,
        String mensagem,
        Map<String, String> campos,
        OffsetDateTime timestamp
) {
}
