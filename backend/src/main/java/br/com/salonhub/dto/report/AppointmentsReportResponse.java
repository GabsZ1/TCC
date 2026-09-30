package br.com.salonhub.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Relatório de agenda/comparecimento: totais e taxas gerais (mesmas regras de {@link AppointmentsReportEntry}). */
public record AppointmentsReportResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<AppointmentsReportEntry> entries,
        long totalConfirmed,
        long totalCompleted,
        long totalCancelled,
        long totalInvalidated,
        long totalNoShows,
        BigDecimal completionRate,
        BigDecimal attendanceRate,
        BigDecimal cancellationRate,
        BigDecimal noShowRate) {
}
