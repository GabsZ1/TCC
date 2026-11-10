package br.com.salonhub.dto.commission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Define os dados devolvidos pela API no fluxo de comissões, sem expor as entidades persistidas. */
public record CommissionReportResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<CommissionReportEntry> entries,
        BigDecimal totalCommission) {
}
