package br.com.salonhub.service.professionalagenda;

import br.com.salonhub.dto.professionalearnings.CompletedServiceEntry;
import br.com.salonhub.dto.professionalearnings.CompletedServicesSummary;
import br.com.salonhub.dto.professionalearnings.ProfessionalCompletedServicesResponse;
import br.com.salonhub.dto.professionalearnings.ServiceOption;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CompletedAppointmentsTotalsRow;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Clock;
import java.util.List;

/**
 * US 2.15 — Visualizar atendimentos realizados (visão do próprio funcionário).
 * <ul>
 *   <li>Só atendimentos CONCLUÍDOS; cancelados e invalidados nunca aparecem.</li>
 *   <li>O período considera a data de conclusão; sem filtro, usa o mês corrente.</li>
 *   <li>O profissional é sempre o do token (RNF.02.01): não há como pedir dados de outro.</li>
 *   <li>Valores e porcentagens vêm do agendamento (congelados), com duas casas decimais.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProfessionalCompletedServicesService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final AppointmentRepository appointmentRepository;
    private final EstablishmentServiceRepository establishmentServiceRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final Clock clock;

    /** Usa o mês corrente por padrão e calcula os totais sobre todo o filtro, além da página exibida. */
    @Transactional(readOnly = true)
    public ProfessionalCompletedServicesResponse findMine(
            Long userId, LocalDate startDate, LocalDate endDate, Long serviceId, int page, int size) {
        User professional = tenantAuthorizationService.requireProfessional(userId);

        if ((startDate == null) != (endDate == null)) {
            throw new InvalidRequestException("Informe a data inicial e a data final para filtrar por período.");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new InvalidRequestException("Período informado é inválido: a data inicial é posterior à data final.");
        }
        if (serviceId != null && establishmentServiceRepository
                .findByIdAndEstablishmentId(serviceId, professional.getEstablishment().getId()).isEmpty()) {
            throw new InvalidRequestException("Tipo de serviço não encontrado neste estabelecimento.");
        }

        LocalDate periodStart = startDate;
        LocalDate periodEnd = endDate;
        if (periodStart == null) {
            YearMonth currentMonth = YearMonth.now(clock);
            periodStart = currentMonth.atDay(1);
            periodEnd = currentMonth.atEndOfMonth();
        }

        var result = appointmentRepository.findCompletedByProfessional(
                        professional.getId(),
                        periodStart.atStartOfDay(),
                        periodEnd.plusDays(1).atStartOfDay(),
                        serviceId, PageRequest.of(page, size));
        List<CompletedServiceEntry> entries = result
                .stream()
                .map(CompletedServiceEntry::fromEntity)
                .toList();
        CompletedAppointmentsTotalsRow totals = appointmentRepository.sumCompletedByProfessional(
                professional.getId(), periodStart.atStartOfDay(), periodEnd.plusDays(1).atStartOfDay(), serviceId);

        List<ServiceOption> availableServices =
                appointmentRepository.findCompletedServiceOptionsByProfessional(professional.getId());

        return new ProfessionalCompletedServicesResponse(
                periodStart, periodEnd, serviceId, entries, summarize(totals), availableServices,
                result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    private CompletedServicesSummary summarize(CompletedAppointmentsTotalsRow totals) {
        BigDecimal totalValue = (totals.getTotalServiceValue() == null ? ZERO : totals.getTotalServiceValue())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCommission = (totals.getTotalCommission() == null ? ZERO : totals.getTotalCommission())
                .setScale(2, RoundingMode.HALF_UP);
        return new CompletedServicesSummary(totals.getTotalAppointments(), totalValue, totalCommission);
    }
}
