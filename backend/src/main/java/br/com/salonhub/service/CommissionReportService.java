package br.com.salonhub.service;

import br.com.salonhub.dto.commission.CommissionReportEntry;
import br.com.salonhub.dto.commission.CommissionReportResponse;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CommissionReportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Relatório de comissões por funcionário e período (US 2.21). Só considera
 * atendimentos com status CONCLUÍDO — cancelados e invalidados nunca entram
 * no cálculo — e filtra pela data de conclusão do atendimento (não pela
 * data agendada), conforme a regra de negócio da user story.
 */
@Service
@RequiredArgsConstructor
public class CommissionReportService {

    private final AppointmentRepository appointmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CommissionReportResponse getReport(
            Long administratorId, LocalDate startDate, LocalDate endDate, Long professionalId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();

        if ((startDate == null) != (endDate == null)) {
            throw new InvalidRequestException("Informe a data inicial e a data final para filtrar por período.");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new InvalidRequestException("A data inicial não pode ser posterior à data final.");
        }

        LocalDate periodStart = startDate;
        LocalDate periodEnd = endDate;
        if (periodStart == null) {
            YearMonth currentMonth = YearMonth.now(clock);
            periodStart = currentMonth.atDay(1);
            periodEnd = currentMonth.atEndOfMonth();
        }

        LocalDateTime periodStartInclusive = periodStart.atStartOfDay();
        LocalDateTime periodEndExclusive = periodEnd.plusDays(1).atStartOfDay();

        List<CommissionReportRow> rows = appointmentRepository.findCommissionReportRows(
                establishmentId, AppointmentStatus.COMPLETED, periodStartInclusive, periodEndExclusive, professionalId);

        List<CommissionReportEntry> entries = rows.stream().map(this::toEntry).toList();
        BigDecimal totalCommission = entries.stream()
                .map(CommissionReportEntry::totalCommission)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        return new CommissionReportResponse(periodStart, periodEnd, entries, totalCommission);
    }

    private CommissionReportEntry toEntry(CommissionReportRow row) {
        BigDecimal averagePercentage = row.getTotalServiceValue().signum() == 0
                ? BigDecimal.ZERO.setScale(2)
                : row.getTotalCommission()
                        .multiply(new BigDecimal("100"))
                        .divide(row.getTotalServiceValue(), 2, RoundingMode.HALF_UP);
        return new CommissionReportEntry(
                row.getProfessionalId(),
                row.getProfessionalName(),
                row.getCompletedServicesCount(),
                row.getTotalServiceValue(),
                averagePercentage,
                row.getTotalCommission());
    }
}
