package br.com.salonhub.service.appointment;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.exception.UnauthorizedException;
import org.springframework.security.access.AccessDeniedException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TenantAuthorizationService;
import br.com.salonhub.service.professionalagenda.ProfessionalAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppointmentCommandService {

    private final AppointmentRepository appointmentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final ProfessionalAvailabilityService professionalAvailabilityService;
    private final Clock clock;

    @Transactional
    public AppointmentResponse create(Long clientId, Long establishmentId, AppointmentCreateRequest request) {
        User client = lockClient(clientId);
        Establishment establishment = findActiveEstablishmentForUpdate(establishmentId);
        EstablishmentService service = findActiveService(request.serviceId(), establishmentId);
        User professional = findAndLockProfessional(request.professionalId(), establishmentId);
        requireProfessionalOffersService(professional, service);

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        validateSchedule(establishment, professional, client, startsAt, endsAt, null);

        Appointment appointment = Appointment.schedule(establishment, client, professional, service, startsAt);
        return AppointmentResponse.fromEntity(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse update(Long clientId, Long appointmentId, AppointmentCreateRequest request) {
        User client = lockClient(clientId);

        // This first read only discovers the establishment needed by the global lock order.
        // State and ownership are checked again after the appointment row is locked.
        Long establishmentId = appointmentRepository
                .findEstablishmentIdByIdAndClientId(appointmentId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));

        Establishment establishment = findActiveEstablishmentForUpdate(establishmentId);
        EstablishmentService service = findActiveService(request.serviceId(), establishmentId);
        User professional = findAndLockProfessional(request.professionalId(), establishmentId);
        requireProfessionalOffersService(professional, service);

        Appointment appointment = appointmentRepository
                .findByIdAndClientIdForUpdate(appointmentId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        if (!appointment.getEstablishment().getId().equals(establishmentId)
                || appointment.getStatus() != AppointmentStatus.CONFIRMED
                || !appointment.getStartsAt().isAfter(LocalDateTime.now(clock))) {
            throw new ConflictException("Apenas agendamentos futuros e confirmados podem ser editados.");
        }

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        validateSchedule(establishment, professional, client, startsAt, endsAt, appointment.getId());

        appointment.update(service, professional, startsAt);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long clientId, Long appointmentId) {
        User client = tenantAuthorizationService.requireClient(clientId);
        Appointment appointment = appointmentRepository.findByIdAndClientIdForUpdate(appointmentId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED
                || !appointment.getStartsAt().isAfter(LocalDateTime.now(clock))) {
            throw new ConflictException("O agendamento não pode ser cancelado.");
        }
        appointment.cancel();
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse cancelAsAdministrator(Long administratorId, Long appointmentId, String reason) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Appointment appointment = appointmentRepository
                .findByIdAndEstablishmentIdForUpdate(appointmentId, administrator.getEstablishment().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException("Apenas agendamentos confirmados podem ser cancelados.");
        }
        appointment.cancelByAdministrator(reason.trim(), administrator, LocalDateTime.now(clock));
        return AppointmentResponse.fromEntity(appointment);
    }
    @Transactional
    public AppointmentResponse complete(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        requireOwningProfessionalOrAdministrator(userId, appointment);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException("Apenas agendamentos confirmados podem ser concluídos.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (appointment.getStartsAt().isAfter(now)) {
            throw new ConflictException("O agendamento só pode ser concluído a partir do horário previsto de início.");
        }
        appointment.complete(now);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse invalidate(Long administratorId, Long appointmentId, String reason) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Appointment appointment = appointmentRepository
                .findByIdAndEstablishmentIdForUpdate(appointmentId, administrator.getEstablishment().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ConflictException("Apenas agendamentos concluídos podem ser invalidados.");
        }
        appointment.invalidate(reason);
        return AppointmentResponse.fromEntity(appointment);
    }

    private void requireOwningProfessionalOrAdministrator(Long userId, Appointment appointment) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));

        boolean isOwningProfessional = user.getRole() == UserRole.PROFESSIONAL
                && user.getId().equals(appointment.getProfessional().getId());
        boolean isEstablishmentAdministrator = user.getRole() == UserRole.ADMIN
                && user.getEstablishment() != null
                && user.getEstablishment().getId().equals(appointment.getEstablishment().getId());

        if (!isOwningProfessional && !isEstablishmentAdministrator) {
            throw new AccessDeniedException("Você não tem permissão para concluir este agendamento.");
        }
    }

    private void validateSchedule(
            Establishment establishment,
            User professional,
            User client,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long ignoredAppointmentId) {
        validateSlotAlignment(startsAt);
        validateAdvanceWindow(establishment, startsAt);
        // O horário de funcionamento do estabelecimento já é considerado por
        // professionalAvailabilityService.validateAvailable(), que calcula a interseção entre
        // o expediente do salão e a jornada própria do profissional (quando houver). Isso evita
        // ter duas fontes independentes de verdade sobre o horário de atendimento.
        professionalAvailabilityService.validateAvailable(professional, startsAt, endsAt);
        validateProfessionalAvailability(professional.getId(), startsAt, endsAt, ignoredAppointmentId);
        validateClientAvailability(client.getId(), startsAt, endsAt, ignoredAppointmentId);
    }

    private void validateSlotAlignment(LocalDateTime startsAt) {
        if (!AppointmentSlotPolicy.isAligned(startsAt)) {
            throw new ConflictException("O horário deve estar alinhado aos intervalos de 30 minutos.");
        }
    }

    private Establishment findActiveEstablishmentForUpdate(Long establishmentId) {
        return establishmentRepository.findByIdForUpdate(establishmentId)
                .filter(Establishment::acceptsNewAppointments)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
    }
    private EstablishmentService findActiveService(Long serviceId, Long establishmentId) {
        return serviceRepository.findByIdAndEstablishmentIdAndActiveTrue(serviceId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
    }

    private User findAndLockProfessional(Long professionalId, Long establishmentId) {
        return userRepository.findByIdForUpdate(professionalId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.PROFESSIONAL)
                .filter(user -> user.getEstablishment() != null)
                .filter(user -> user.getEstablishment().getId().equals(establishmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
    }

    private User lockClient(Long clientId) {
        tenantAuthorizationService.requireClient(clientId);
        return userRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
    }

    private void requireProfessionalOffersService(User professional, EstablishmentService service) {
        if (!professional.offers(service)) {
            throw new ConflictException("O profissional selecionado não oferece este serviço.");
        }
    }

    private void validateProfessionalAvailability(
            Long professionalId, LocalDateTime startsAt, LocalDateTime endsAt, Long ignoredAppointmentId) {
        boolean conflict = ignoredAppointmentId == null
                ? appointmentRepository.existsByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                        professionalId, AppointmentStatus.CONFIRMED, endsAt, startsAt)
                : appointmentRepository.existsByProfessionalIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        professionalId, AppointmentStatus.CONFIRMED, ignoredAppointmentId, endsAt, startsAt);
        if (conflict) {
            throw new ConflictException("O funcionário já possui um agendamento nesse período.");
        }
    }

    private void validateClientAvailability(
            Long clientId, LocalDateTime startsAt, LocalDateTime endsAt, Long ignoredAppointmentId) {
        boolean conflict = ignoredAppointmentId == null
                ? appointmentRepository.existsByClientIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                        clientId, AppointmentStatus.CONFIRMED, endsAt, startsAt)
                : appointmentRepository.existsByClientIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        clientId, AppointmentStatus.CONFIRMED, ignoredAppointmentId, endsAt, startsAt);
        if (conflict) {
            throw new ConflictException("Você já possui um agendamento nesse período.");
        }
    }

    private void validateAdvanceWindow(Establishment establishment, LocalDateTime startsAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (startsAt.isBefore(now.plusMinutes(establishment.getMinimumAdvanceMinutes()))) {
            throw new ConflictException("O agendamento exige pelo menos "
                    + formatMinimumAdvance(establishment.getMinimumAdvanceMinutes()) + " de antecedência.");
        }
        if (startsAt.isAfter(now.plusDays(establishment.getMaximumAdvanceDays()))) {
            throw new ConflictException("O agendamento pode ser feito com no máximo "
                    + establishment.getMaximumAdvanceDays() + " dias de antecedência.");
        }
    }

    private String formatMinimumAdvance(int minutes) {
        if (minutes % 60 == 0) {
            return (minutes / 60) + (minutes == 60 ? " hora" : " horas");
        }
        return minutes + " minutos";
    }

}
