package br.com.salonhub.dto.professionalearnings;

import java.math.BigDecimal;

/** Define os dados devolvidos pela API no fluxo de serviços concluídos do profissional, sem expor as entidades persistidas. */
public record CompletedServicesSummary(
        long totalServices,
        BigDecimal totalServiceValue,
        BigDecimal totalCommission
) {
}
