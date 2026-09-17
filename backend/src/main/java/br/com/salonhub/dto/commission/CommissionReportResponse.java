package br.com.salonhub.dto.commission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CommissionReportResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<CommissionReportEntry> entries,
        BigDecimal totalCommission) {
}
