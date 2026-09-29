package br.com.salonhub.dto.report;

import java.math.BigDecimal;

/**
 * Linha do relatório de atendimentos por profissional e período: contagem
 * de agendamentos por situação (confirmado, concluído, cancelado e
 * invalidado), agrupados pela data agendada (startsAt) do atendimento —
 * diferente do relatório de comissões, que agrupa pela data de conclusão.
 * {@code completionRate} considera apenas os atendimentos já resolvidos
 * (concluídos, cancelados ou invalidados); confirmados ainda não entram
 * nessa taxa por não terem um desfecho definido.
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
        BigDecimal completionRate) {
}
