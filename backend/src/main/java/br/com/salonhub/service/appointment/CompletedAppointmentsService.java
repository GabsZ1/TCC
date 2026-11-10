package br.com.salonhub.service.appointment;

import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.dto.report.CompletedAppointmentEntry;
import br.com.salonhub.dto.report.CompletedAppointmentsResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CompletedAppointmentsTotalsRow;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * US 2.13 — Visualizar e filtrar atendimentos dos funcionários (visão do administrador).
 * <ul>
 *   <li>Só atendimentos FINALIZADOS (COMPLETED); invalidados e cancelados não aparecem.</li>
 *   <li>O período considera a data de conclusão. Sem filtro, mostra todo o histórico.</li>
 *   <li>Filtro por funcionário só aceita profissionais do próprio estabelecimento.</li>
 *   <li>Paginação no servidor (RNF.03.02); os totais consideram todos os resultados filtrados.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CompletedAppointmentsService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    /** Limites usados quando não há filtro de período (equivale a "todo o histórico"). */
    private static final LocalDateTime NO_LOWER_BOUND = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime NO_UPPER_BOUND = LocalDateTime.of(9999, 1, 1, 0, 0);

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    /** Filtra atendimentos concluídos por período e devolve os totais do mesmo recorte. */
    @Transactional(readOnly = true)
    public CompletedAppointmentsResponse find(
            Long administratorId,
            LocalDate startDate,
            LocalDate endDate,
            Long professionalId,
            Integer page,
            Integer size) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();

        if ((startDate == null) != (endDate == null)) {
            throw new InvalidRequestException("Informe a data inicial e a data final para filtrar por período.");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new InvalidRequestException("Período informado é inválido");
        }
        if (professionalId != null && userRepository
                .findByIdAndEstablishmentIdAndRole(professionalId, establishmentId, UserRole.PROFESSIONAL)
                .isEmpty()) {
            throw new InvalidRequestException("Funcionário não encontrado neste estabelecimento.");
        }

        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "Paginação inválida: a página começa em 0 e o tamanho deve ficar entre 1 e " + MAX_PAGE_SIZE + ".");
        }

        LocalDateTime periodStart = startDate == null ? NO_LOWER_BOUND : startDate.atStartOfDay();
        LocalDateTime periodEnd = endDate == null ? NO_UPPER_BOUND : endDate.plusDays(1).atStartOfDay();

        Page<Appointment> result = appointmentRepository.findCompletedByEstablishment(
                establishmentId, periodStart, periodEnd, professionalId, PageRequest.of(pageNumber, pageSize));
        CompletedAppointmentsTotalsRow totals = appointmentRepository.sumCompletedByEstablishment(
                establishmentId, periodStart, periodEnd, professionalId);

        return new CompletedAppointmentsResponse(
                startDate,
                endDate,
                professionalId,
                result.getContent().stream().map(CompletedAppointmentEntry::fromEntity).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                result.getTotalElements(),
                money(totals == null ? null : totals.getTotalServiceValue()),
                money(totals == null ? null : totals.getTotalCommission()));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
