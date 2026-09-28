package br.com.salonhub.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta da US 2.13. Sem filtro de período, {@code startDate}/{@code endDate} vêm nulos
 * e a consulta considera todo o histórico. Os totais somam TODOS os atendimentos que
 * atendem aos filtros (não só os da página atual).
 */
public record CompletedAppointmentsResponse(
        LocalDate startDate,
        LocalDate endDate,
        Long professionalId,
        List<CompletedAppointmentEntry> entries,
        int page,
        int size,
        int totalPages,
        long totalElements,
        BigDecimal totalServiceValue,
        BigDecimal totalCommission
) {
}
