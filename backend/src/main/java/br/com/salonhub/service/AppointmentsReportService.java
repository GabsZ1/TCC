package br.com.salonhub.service;

import br.com.salonhub.dto.report.AppointmentsReportEntry;
import br.com.salonhub.dto.report.AppointmentsReportResponse;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.AppointmentsReportRow;
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
 * Relatório de atendimentos por profissional e período: quantos
 * agendamentos cada profissional teve em cada situação (confirmado,
 * concluído, cancelado, invalidado), agrupados pela data agendada
 * (startsAt) — ao contrário do relatório de comissões, que olha só para
 * atendimentos concluídos e agrupa pela data de conclusão, este relatório
 * mostra o volume total de agendamentos recebidos por cada profissional,
 * incluindo os que não deram certo, servindo como um indicador de
 * ocupação e de taxa de cancelamento/invalidação por profissional.
 */
@Service
@RequiredArgsConstructor
public class AppointmentsReportService {

    private final AppointmentRepository appointmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AppointmentsReportResponse getReport(
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

        List<AppointmentsReportRow> rows = appointmentRepository.findAppointmentsReportRows(
                establishmentId, periodStartInclusive, periodEndExclusive, professionalId);

        List<AppointmentsReportEntry> entries = rows.stream().map(this::toEntry).toList();

        return new AppointmentsReportResponse(
                periodStart,
                periodEnd,
                entries,
                entries.stream().mapToLong(AppointmentsReportEntry::confirmedCount).sum(),
                entries.stream().mapToLong(AppointmentsReportEntry::completedCount).sum(),
                entries.stream().mapToLong(AppointmentsReportEntry::cancelledCount).sum(),
                entries.stream().mapToLong(AppointmentsReportEntry::invalidatedCount).sum(),
                entries.stream().mapToLong(AppointmentsReportEntry::noShowCount).sum());
    }

    private AppointmentsReportEntry toEntry(AppointmentsReportRow row) {
        long resolvedCount = row.getCompletedCount() + row.getCancelledCount()
                + row.getInvalidatedCount() + row.getNoShowCount();
        BigDecimal completionRate = resolvedCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(row.getCompletedCount())
                        .multiply(new BigDecimal("100"))
                        .divide(BigDecimal.valueOf(resolvedCount), 2, RoundingMode.HALF_UP);
        long totalCount = row.getConfirmedCount() + resolvedCount;
        return new AppointmentsReportEntry(
                row.getProfessionalId(),
                row.getProfessionalName(),
                row.getConfirmedCount(),
                row.getCompletedCount(),
                row.getCancelledCount(),
                row.getInvalidatedCount(),
                row.getNoShowCount(),
                totalCount,
                completionRate);
    }
}
