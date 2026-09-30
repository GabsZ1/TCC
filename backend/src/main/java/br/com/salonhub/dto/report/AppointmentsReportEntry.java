package br.com.salonhub.dto.report;

import java.math.BigDecimal;

/**
 * Linha do relatório de agenda/comparecimento por profissional, agrupada pela data agendada (startsAt).
 *
 * As taxas usam só os desfechos da agenda (concluído, cancelado e falta):
 * - completionRate: concluídos / desfechos;
 * - cancellationRate: cancelados / desfechos;
 * - noShowRate: faltas / desfechos;
 * - attendanceRate: concluídos / (concluídos + faltas), ou seja, dos clientes que não cancelaram, quantos vieram.
 * Confirmados ainda não têm desfecho. Invalidados são correções de registro, não comportamento da agenda,
 * então aparecem na contagem mas não entram em nenhuma taxa.
 */
public record AppointmentsReportEntry(
        Long professionalId,
        String professionalName,
        long confirmedCount,
        long completedCount,
        long cancelledCount,
        long invalidatedCount,
        long noShowCount,
        long totalCount,
        BigDecimal completionRate,
        BigDecimal attendanceRate,
        BigDecimal cancellationRate,
        BigDecimal noShowRate) {
}
