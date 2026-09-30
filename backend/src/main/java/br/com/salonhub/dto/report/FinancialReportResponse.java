package br.com.salonhub.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório financeiro/produção: atendimentos concluídos no período (pela data de conclusão).
 * "Valor após comissões" não é lucro: o sistema não conhece aluguel, produtos, impostos etc.
 */
public record FinancialReportResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        long completedCount,
        BigDecimal grossRevenue,
        BigDecimal totalCommission,
        BigDecimal revenueAfterCommission,
        BigDecimal averageTicket,
        List<Line> byService,
        List<Line> byProfessional) {

    public record Line(Long id, String name, long completedCount, BigDecimal revenue,
                       BigDecimal commission, BigDecimal averageTicket) {
    }
}
