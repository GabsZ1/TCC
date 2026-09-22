package br.com.salonhub.service;

import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.dto.establishment.EstablishmentUpdateRequest;
import br.com.salonhub.dto.establishment.BusinessHourRequest;
import br.com.salonhub.dto.establishment.BusinessHourResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstablishmentManagementService {

    private final EstablishmentRepository establishmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EstablishmentResponse findMine(Long administratorId) {
        return response(findEstablishment(administratorId));
    }

    @Transactional
    public EstablishmentResponse update(Long administratorId, EstablishmentUpdateRequest request) {
        Establishment identityEstablishment = findEstablishment(administratorId);
        Establishment establishment = establishmentRepository.findByIdForUpdate(identityEstablishment.getId())
                .orElseThrow(() -> new IllegalStateException("Estabelecimento do administrador não encontrado."));
        if (!establishment.isActive()) {
            throw new ConflictException("Não é possível editar um estabelecimento inativo.");
        }

        if (request.businessHours() != null) {
            validateBusinessHours(request.businessHours());
            requireNoConflictingConfirmedAppointments(establishment, request.businessHours());
        }

        establishment.updateRegistration(
                request.name().trim(),
                normalizeOptionalText(request.email()),
                normalizeOptionalText(request.phone())
        );
        if (request.businessHours() != null) {
            updateBusinessHours(establishment, request.businessHours());
        }
        if (request.minimumAdvanceHours() != null && request.maximumAdvanceDays() != null) {
            int minimumMinutes = request.minimumAdvanceHours() * 60;
            if (minimumMinutes > request.maximumAdvanceDays() * 1440) {
                throw new InvalidRequestException("A antecedência mínima não pode ser maior que a janela máxima de agendamento.");
            }
            establishment.updateAppointmentAdvancePolicy(minimumMinutes, request.maximumAdvanceDays());
        }
        return response(establishment);
    }

    @Transactional
    public EstablishmentResponse blockBookings(Long administratorId) {
        Establishment establishment = lockMine(administratorId);
        establishment.blockBookings(false);
        return response(establishment);
    }

    @Transactional
    public EstablishmentResponse allowBookings(Long administratorId) {
        Establishment establishment = lockMine(administratorId);
        if (establishment.isBookingsBlockedBySystem()) {
            throw new ConflictException("O bloqueio foi aplicado pelo administrador do sistema e só pode ser removido por ele.");
        }
        establishment.allowBookings();
        return response(establishment);
    }

    @Transactional
    public EstablishmentResponse pause(Long administratorId) {
        Establishment establishment = lockMine(administratorId);
        requireNoPendingAppointments(establishment);
        establishment.pause(false);
        return response(establishment);
    }

    @Transactional
    public EstablishmentResponse resume(Long administratorId) {
        Establishment establishment = lockMine(administratorId);
        if (establishment.isPausedBySystem()) {
            throw new ConflictException("A pausa foi aplicada pelo administrador do sistema e só pode ser removida por ele.");
        }
        establishment.resume();
        return response(establishment);
    }

    @Transactional
    public void blockBookingsAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).blockBookings(true);
    }

    @Transactional
    public void allowBookingsAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).allowBookings();
    }

    @Transactional
    public void pauseAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        Establishment establishment = lockEstablishment(establishmentId);
        requireNoPendingAppointments(establishment);
        establishment.pause(true);
    }

    @Transactional
    public void resumeAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).resume();
    }

    @Transactional
    public void deactivateAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        var systemAdministrator = tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        Establishment establishment = lockEstablishment(establishmentId);
        if (!establishment.isActive()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        appointmentRepository.findByEstablishmentIdAndStatus(establishmentId, AppointmentStatus.CONFIRMED)
                .forEach(appointment -> appointment.cancelByAdministrator(
                        "Site desativado", systemAdministrator, now));
        establishment.deactivate();
    }

    @Transactional
    public void reactivateAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).reactivate();
    }

    private Establishment findEstablishment(Long administratorId) {
        return tenantAuthorizationService.requireAdministratorIdentity(administratorId).getEstablishment();
    }

    private Establishment lockMine(Long administratorId) {
        Establishment identityEstablishment = findEstablishment(administratorId);
        return lockEstablishment(identityEstablishment.getId());
    }

    private Establishment lockEstablishment(Long establishmentId) {
        return establishmentRepository.findByIdForUpdate(establishmentId)
                .orElseThrow(() -> new br.com.salonhub.exception.ResourceNotFoundException(
                        "Estabelecimento não encontrado."));
    }

    private void requireNoPendingAppointments(Establishment establishment) {
        if (appointmentRepository.existsByEstablishmentIdAndStatus(
                establishment.getId(), AppointmentStatus.CONFIRMED)) {
            throw new ConflictException(
                    "Não é possível pausar enquanto houver atendimentos pendentes. "
                            + "Cancele ou conclua todos os atendimentos confirmados.");
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void updateBusinessHours(Establishment establishment, java.util.List<BusinessHourRequest> requests) {
        requests.forEach(request -> {
            EstablishmentBusinessHour hour = businessHourRepository
                    .findByEstablishmentIdAndDayOfWeek(establishment.getId(), request.dayOfWeek())
                    .orElseGet(() -> EstablishmentBusinessHour.builder()
                            .establishment(establishment).dayOfWeek(request.dayOfWeek()).build());
            hour.update(request.closed(), request.openingTime(), request.closingTime());
            if (hour.getId() == null) {
                businessHourRepository.save(hour);
            }
        });
    }

    private void validateBusinessHours(List<BusinessHourRequest> requests) {
        if (requests.stream().map(BusinessHourRequest::dayOfWeek).distinct().count() != 7) {
            throw new InvalidRequestException("Informe cada dia da semana uma única vez.");
        }
        requests.forEach(request -> {
            if (!request.closed() && (request.openingTime() == null || request.closingTime() == null
                    || !request.openingTime().isBefore(request.closingTime()))) {
                throw new InvalidRequestException("O horário de abertura deve ser anterior ao fechamento.");
            }
        });
    }

    /**
     * Impede reduzir o expediente de forma que um atendimento confirmado futuro ou em
     * andamento fique fora do novo horário. O estabelecimento é bloqueado antes desta
     * verificação, usando a mesma ordem de lock da criação de agendamentos, para que uma
     * nova reserva não possa entrar entre a validação e a gravação do expediente.
     */
    private void requireNoConflictingConfirmedAppointments(
            Establishment establishment, List<BusinessHourRequest> requests) {
        List<Appointment> relevantConfirmed = appointmentRepository
                .findByEstablishmentIdAndStatusAndEndsAtGreaterThanOrderByStartsAt(
                        establishment.getId(), AppointmentStatus.CONFIRMED, LocalDateTime.now(clock));
        if (relevantConfirmed.isEmpty()) {
            return;
        }

        Map<DayOfWeek, BusinessHourRequest> hoursByDay = requests.stream()
                .collect(Collectors.toMap(BusinessHourRequest::dayOfWeek, Function.identity()));
        long conflicting = relevantConfirmed.stream()
                .filter(appointment -> fallsOutsideNewBusinessHours(appointment, hoursByDay))
                .count();
        if (conflicting > 0) {
            throw new ConflictException(
                    "Não foi possível alterar o expediente: " + conflicting
                            + " agendamento(s) confirmado(s) futuro(s) ou em andamento ficariam fora do novo horário. "
                            + "Cancele-os, aguarde a conclusão ou ajuste o expediente para mantê-los dentro do horário.");
        }
    }

    private boolean fallsOutsideNewBusinessHours(
            Appointment appointment, Map<DayOfWeek, BusinessHourRequest> hoursByDay) {
        BusinessHourRequest hour = hoursByDay.get(appointment.getStartsAt().getDayOfWeek());
        if (hour == null || hour.closed()) {
            return true;
        }
        LocalTime opening = hour.openingTime();
        LocalTime closing = hour.closingTime();
        boolean changesDay = !appointment.getStartsAt().toLocalDate()
                .equals(appointment.getEndsAt().toLocalDate());
        return changesDay
                || appointment.getStartsAt().toLocalTime().isBefore(opening)
                || appointment.getEndsAt().toLocalTime().isAfter(closing);
    }

    private EstablishmentResponse response(Establishment establishment) {
        var hours = businessHourRepository.findByEstablishmentIdOrderByDayOfWeek(establishment.getId())
                .stream().map(BusinessHourResponse::fromEntity).toList();
        return EstablishmentResponse.fromEntity(establishment, hours);
    }
}
