package br.com.salonhub.dto.report;

import java.time.LocalDate;
import java.util.List;

public record AppointmentsReportResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<AppointmentsReportEntry> entries,
        long totalConfirmed,
        long totalCompleted,
        long totalCancelled,
        long totalInvalidated) {
}
