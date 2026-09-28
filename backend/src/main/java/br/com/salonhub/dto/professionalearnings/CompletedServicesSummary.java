package br.com.salonhub.dto.professionalearnings;

import java.math.BigDecimal;

public record CompletedServicesSummary(
        long totalServices,
        BigDecimal totalServiceValue,
        BigDecimal totalCommission
) {
}
