package br.com.salonhub.service.establishment;

import br.com.salonhub.service.tenant.TenantAuthorizationService;
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

/** Aplica mudanças de estado operacional do estabelecimento. */
@Service
@RequiredArgsConstructor
public class EstablishmentOperationService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final AppointmentRepository appointmentRepository;
    private final TenantAuthorizationService authorizationService;
    private final AppointmentNotifier appointmentNotifier;
    private final Clock clock;

    /** Suspende novas reservas sem interromper atendimentos já confirmados. */
    @Transactional
    public EstablishmentResponse blockBookings(Long administratorId) {
        Establishment establishment = lockMine(administratorId);
        establishment.blockBookings(false);
        return response(establishment);
    }

    /** Só libera as reservas quando o bloqueio não foi imposto pela administração do sistema. */
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

    /** Pausa o estabelecimento somente quando não há atendimentos futuros ou em andamento. */
    @Transactional
    public EstablishmentResponse pause(Long administratorId) {
        Establishment establishment = lockMine(administratorId);
        requireNoPendingAppointments(establishment);
        establishment.pause(false);
        return response(establishment);
    }

    /** Só retoma a operação quando a pausa não foi imposta pela administração do sistema. */
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

    /** Registra um bloqueio que o administrador local não pode remover. */
    @Transactional
    public void blockBookingsAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).blockBookings(true);
    }

    /** Remove o bloqueio global de novas reservas. */
    @Transactional
    public void allowBookingsAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).allowBookings();
    }

    /** Pausa a unidade globalmente após verificar atendimentos ainda abertos. */
    @Transactional
    public void pauseAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        Establishment establishment = lockEstablishment(establishmentId);
        requireNoPendingAppointments(establishment);
        establishment.pause(true);
    }

    /** Retoma a operação após uma pausa aplicada pelo sistema. */
    @Transactional
    public void resumeAsSystemAdministrator(Long systemAdministratorId, Long establishmentId) {
        authorizationService.requireSystemAdministrator(systemAdministratorId);
        lockEstablishment(establishmentId).resume();
    }

    /** Cancela as reservas confirmadas e comunica os envolvidos antes de desativar o estabelecimento. */
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
                    appointment.cancelByAdministrator("Estabelecimento desativado", systemAdministrator, now);
                    appointmentNotifier.cancelledByEstablishment(appointment, systemAdministrator);
                });
        establishment.deactivate();
    }

    /** Reativa a unidade sem recriar seus dados e histórico. */
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
