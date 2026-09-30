package br.com.salonhub.service;

import br.com.salonhub.dto.report.FinancialReportResponse;
import br.com.salonhub.entity.User;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.FinancialReportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório financeiro/produção: faturamento, comissões, valor após comissões e ticket médio dos
 * atendimentos concluídos, com detalhamento por serviço e por profissional. Usa os valores
 * combinados no agendamento (snapshot), então alterar o catálogo não muda o histórico.
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final AppointmentRepository appointmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FinancialReportResponse getReport(Long administratorId, LocalDate startDate, LocalDate endDate,
                                             Long professionalId, Long serviceId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();
        ReportPeriod period = ReportPeriod.resolve(startDate, endDate, clock);

        List<FinancialReportResponse.Line> byService = appointmentRepository.findFinancialReportByService(
                        establishmentId, period.startInclusive(), period.endExclusive(), professionalId, serviceId)
                .stream().map(FinancialReportService::toLine).toList();
        List<FinancialReportResponse.Line> byProfessional = appointmentRepository.findFinancialReportByProfessional(
                        establishmentId, period.startInclusive(), period.endExclusive(), professionalId, serviceId)
                .stream().map(FinancialReportService::toLine).toList();

        long completed = byService.stream().mapToLong(FinancialReportResponse.Line::completedCount).sum();
        BigDecimal revenue = byService.stream().map(FinancialReportResponse.Line::revenue).reduce(ZERO, BigDecimal::add);
        BigDecimal commission = byService.stream().map(FinancialReportResponse.Line::commission).reduce(ZERO, BigDecimal::add);
        return new FinancialReportResponse(period.start(), period.end(), completed, revenue, commission,
                revenue.subtract(commission), average(revenue, completed), byService, byProfessional);
    }

    private static FinancialReportResponse.Line toLine(FinancialReportRow row) {
        BigDecimal revenue = scaled(row.getRevenue());
        return new FinancialReportResponse.Line(row.getId(), row.getName(), row.getCompletedCount(), revenue,
                scaled(row.getCommission()), average(revenue, row.getCompletedCount()));
    }

    private static BigDecimal average(BigDecimal total, long count) {
        return count == 0 ? ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
