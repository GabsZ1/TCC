package br.com.salonhub.dto.common;

import java.time.OffsetDateTime;
import java.util.Map;

/** Transporta os dados compartilhados pelo fluxo de erros da API. */
public record ApiError(
        int status,
        String message,
        Map<String, String> fields,
        OffsetDateTime timestamp
) {
}
