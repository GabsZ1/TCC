package br.com.salonhub.service.report;

import br.com.salonhub.service.tenant.TenantAuthorizationService;

import br.com.salonhub.dto.report.AppointmentsReportEntry;
import br.com.salonhub.dto.report.AppointmentsReportResponse;
import br.com.salonhub.entity.User;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.AppointmentsReportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
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

    /** Reúne registros e totais de agendamentos para o período solicitado. */
    @Transactional(readOnly = true)
    public AppointmentsReportResponse getReport(
            Long administratorId, LocalDate startDate, LocalDate endDate, Long professionalId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();
        ReportPeriod period = ReportPeriod.resolve(startDate, endDate, clock);

        List<AppointmentsReportRow> rows = appointmentRepository.findAppointmentsReportRows(
                establishmentId, period.startInclusive(), period.endExclusive(), professionalId);
        List<AppointmentsReportEntry> entries = rows.stream().map(this::toEntry).toList();

        long confirmed = entries.stream().mapToLong(AppointmentsReportEntry::confirmedCount).sum();
        long completed = entries.stream().mapToLong(AppointmentsReportEntry::completedCount).sum();
        long cancelled = entries.stream().mapToLong(AppointmentsReportEntry::cancelledCount).sum();
        long invalidated = entries.stream().mapToLong(AppointmentsReportEntry::invalidatedCount).sum();
        long noShows = entries.stream().mapToLong(AppointmentsReportEntry::noShowCount).sum();
        long outcomes = completed + cancelled + noShows;
        return new AppointmentsReportResponse(
                period.start(), period.end(), entries,
                confirmed, completed, cancelled, invalidated, noShows,
                percentage(completed, outcomes),
                percentage(completed, completed + noShows),
                percentage(cancelled, outcomes),
                percentage(noShows, outcomes));
    }

    /** Calcula indicadores da linha usando apenas os estados que compõem o denominador. */
    private AppointmentsReportEntry toEntry(AppointmentsReportRow row) {
        long completed = row.getCompletedCount();
        long cancelled = row.getCancelledCount();
        long noShows = row.getNoShowCount();
        long outcomes = completed + cancelled + noShows;
        long totalCount = row.getConfirmedCount() + outcomes + row.getInvalidatedCount();
        return new AppointmentsReportEntry(
                row.getProfessionalId(),
                row.getProfessionalName(),
                row.getConfirmedCount(),
                completed,
                cancelled,
                row.getInvalidatedCount(),
                noShows,
                totalCount,
                percentage(completed, outcomes),
                percentage(completed, completed + noShows),
                percentage(cancelled, outcomes),
                percentage(noShows, outcomes));
    }

    private static BigDecimal percentage(long part, long total) {
        return total == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(part).multiply(new BigDecimal("100"))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
