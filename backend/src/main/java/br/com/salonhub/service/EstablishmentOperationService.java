package br.com.salonhub.service;

import br.com.salonhub.dto.establishment.BusinessHourResponse;
import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.notification.AppointmentNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EstablishmentOperationService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final AppointmentRepository appointmentRepository;
    private final TenantAuthorizationService authorizationService;
    private final AppointmentNotifier appointmentNotifier;
    private final Clock clock;

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
            throw new ConflictException(
                    "O bloqueio foi aplicado pelo administrador do sistema e só pode ser removido por ele.");
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
            throw new ConflictException(
                    "A pausa foi aplicada pelo administrador do sistema e só pode ser removida por ele.");
        }
        establishment.resume();
        return response(establishment);
    }

    @Transactional
    public void blockBookingsAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).blockBookings(true);
    }

    @Transactional
    public void allowBookingsAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).allowBookings();
    }

    @Transactional
    public void pauseAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        Establishment establishment = lockEstablishment(establishmentId);
        requireNoPendingAppointments(establishment);
        establishment.pause(true);
    }

    @Transactional
    public void resumeAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).resume();
    }

    @Transactional
    public void deactivateAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        var systemAdministrator = authorizationService.requireSystemAdministrator(systemAdministratorId);
        Establishment establishment = lockEstablishment(establishmentId);
        if (!establishment.isActive()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        appointmentRepository.findByEstablishmentIdAndStatusForUpdate(
                        establishmentId, AppointmentStatus.CONFIRMED)
                .forEach(appointment -> {
                    appointment.cancelByAdministrator("Site desativado", systemAdministrator, now);
                    appointmentNotifier.cancelledByEstablishment(appointment, systemAdministrator);
                });
        establishment.deactivate();
    }

    @Transactional
    public void reactivateAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).reactivate();
    }

    private Establishment lockMine(Long administratorId) {
        Long establishmentId = authorizationService.requireAdministrator(administratorId).getEstablishment().getId();
        return lockEstablishment(establishmentId);
    }

    private Establishment lockEstablishment(Long establishmentId) {
        return establishmentRepository.findByIdForUpdate(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
    }

    private void requireNoPendingAppointments(Establishment establishment) {
        if (appointmentRepository.existsByEstablishmentIdAndStatusAndEndsAtGreaterThan(
                establishment.getId(), AppointmentStatus.CONFIRMED, LocalDateTime.now(clock))) {
            throw new ConflictException(
                    "Não é possível pausar enquanto houver atendimentos futuros em aberto. "
                            + "Cancele ou conclua os atendimentos confirmados.");
        }
    }

    private EstablishmentResponse response(Establishment establishment) {
        var hours = businessHourRepository.findByEstablishmentIdOrderByDayOfWeek(establishment.getId())
                .stream()
                .map(BusinessHourResponse::fromEntity)
                .toList();
        return EstablishmentResponse.fromEntity(establishment, hours);
    }
}
