package br.com.salonhub.service.appointment;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentRescheduleRequest;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.AppointmentSource;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.notification.AppointmentNotifier;
import org.springframework.security.access.AccessDeniedException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TenantAuthorizationService;
import br.com.salonhub.service.CustomerResolutionService;
import br.com.salonhub.service.professionalagenda.ProfessionalAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppointmentCommandService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final CustomerResolutionService customerResolutionService;
    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final ProfessionalAvailabilityService professionalAvailabilityService;
    private final AppointmentNotifier notifier;
    private final Clock clock;

    @Transactional
    public AppointmentResponse create(Long clientId, Long establishmentId, AppointmentCreateRequest request) {
        User client = lockClient(clientId);
        Establishment establishment = findActiveEstablishmentForUpdate(establishmentId);
        Customer customer = customerResolutionService.resolveForBooking(establishment, client);
        EstablishmentService service = findActiveService(request.serviceId(), establishmentId);
        User professional = findAndLockProfessional(request.professionalId(), establishmentId);
        requireProfessionalOffersService(professional, service);

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        validateSchedule(establishment, professional, customer, startsAt, endsAt, null, true);

        Appointment appointment = Appointment.schedule(establishment, customer, professional, service, startsAt);
        appointment.recordCreation(AppointmentSource.CLIENT_ONLINE, client);
        Appointment saved = appointmentRepository.save(appointment);
        notifier.booked(saved, client);
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponse createForCustomer(Long administratorId, Long customerId,
                                                 AppointmentCreateRequest request) {
        User administrator = tenantAuthorizationService.requireEstablishmentStaff(administratorId);
        // O administrador agenda para qualquer profissional; o profissional, só para si mesmo.
        if (administrator.getRole() == UserRole.PROFESSIONAL
                && !administrator.getId().equals(request.professionalId())) {
            throw new AccessDeniedException("O profissional só pode criar agendamentos na própria agenda.");
        }
        Long establishmentId = administrator.getEstablishment().getId();
        Establishment establishment = findActiveEstablishmentForUpdate(establishmentId);
        Customer customer = customerRepository.findByIdAndEstablishmentId(customerId, establishmentId)
                .filter(Customer::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        EstablishmentService service = findActiveService(request.serviceId(), establishmentId);
        User professional = findAndLockProfessional(request.professionalId(), establishmentId);
        requireProfessionalOffersService(professional, service);
        LocalDateTime startsAt = request.startsAt();
        validateSchedule(establishment, professional, customer, startsAt,
                startsAt.plusMinutes(service.getDurationMinutes()), null, false);
        Appointment appointment = Appointment.schedule(establishment, customer, professional, service, startsAt);
        appointment.recordCreation(administrator.getRole() == UserRole.ADMIN
                ? AppointmentSource.ADMIN : AppointmentSource.PROFESSIONAL, administrator);
        Appointment saved = appointmentRepository.save(appointment);
        notifier.booked(saved, administrator);
        return AppointmentResponse.fromEntity(saved);
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
        boolean changesService = !service.getId().equals(appointment.getService().getId());
        int durationMinutes = changesService ? service.getDurationMinutes() : appointment.getDurationMinutes();
        LocalDateTime endsAt = startsAt.plusMinutes(durationMinutes);
        validateSchedule(establishment, professional, appointment.getCustomer(), startsAt, endsAt, appointment.getId(), true);

        AppointmentNotifier.PreviousSchedule before = AppointmentNotifier.PreviousSchedule.of(appointment);
        if (changesService) {
            appointment.update(service, professional, startsAt);
        } else {
            appointment.reschedule(professional, startsAt);
        }
        notifier.rescheduled(appointment, before, client);
        return AppointmentResponse.fromEntity(appointment);
    }

    /**
     * Remarcação e troca de profissional pelo administrador (ex.: profissional faltou, cliente pediu
     * outro horário). Passa pelas mesmas validações de disponibilidade e conflito do agendamento,
     * mas sem a antecedência mínima do agendamento online.
     */
    @Transactional
    public AppointmentResponse rescheduleAsAdministrator(
            Long administratorId, Long appointmentId, AppointmentRescheduleRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();
        Appointment appointmentToReschedule = appointmentRepository
                .findByIdAndEstablishmentId(appointmentId, establishmentId)
                .orElse(null);
        if (appointmentToReschedule != null) {
            lockCustomerUser(appointmentToReschedule.getCustomer());
        }

        // Mesma ordem de locks da criação: estabelecimento, profissional e, por fim, o agendamento.
        Establishment establishment = findActiveEstablishmentForUpdate(establishmentId);
        User professional = findAndLockProfessional(request.professionalId(), establishmentId);
        Appointment appointment = appointmentRepository.findByIdAndEstablishmentIdForUpdate(appointmentId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED
                || !appointment.getStartsAt().isAfter(LocalDateTime.now(clock))) {
            throw new ConflictException("Apenas agendamentos confirmados que ainda não começaram podem ser remarcados.");
        }

        boolean changesService = request.serviceId() != null
                && !request.serviceId().equals(appointment.getService().getId());
        EstablishmentService service = changesService
                ? findActiveService(request.serviceId(), establishmentId)
                : appointment.getService();
        requireProfessionalOffersService(professional, service);

        LocalDateTime startsAt = request.startsAt();
        int durationMinutes = changesService ? service.getDurationMinutes() : appointment.getDurationMinutes();
        validateSchedule(establishment, professional, appointment.getCustomer(), startsAt,
                startsAt.plusMinutes(durationMinutes), appointment.getId(), false);

        AppointmentNotifier.PreviousSchedule before = AppointmentNotifier.PreviousSchedule.of(appointment);
        if (changesService) {
            appointment.update(service, professional, startsAt);
        } else {
            appointment.reschedule(professional, startsAt);
        }
        notifier.rescheduled(appointment, before, administrator);
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
        appointment.cancel(client, LocalDateTime.now(clock));
        notifier.cancelledByClient(appointment);
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
        notifier.cancelledByEstablishment(appointment, administrator);
        return AppointmentResponse.fromEntity(appointment);
    }
    @Transactional
    public AppointmentResponse complete(Long userId, Long appointmentId) {
        User user = tenantAuthorizationService.requireEstablishmentStaff(userId);
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        requireOwningProfessionalOrAdministrator(user, appointment);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException("Apenas agendamentos confirmados podem ser concluídos.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (appointment.getStartsAt().isAfter(now)) {
            throw new ConflictException("O agendamento só pode ser concluído a partir do horário previsto de início.");
        }
        appointment.complete(now, userRepository.getReferenceById(userId));
        return AppointmentResponse.fromEntity(appointment);
    }

    /**
     * Tolerância de atraso: a falta pode ser registrada a partir do início + 15 min (não é preciso
     * esperar o fim previsto). É sempre manual; não há marcação automática.
     */
    public static final Duration NO_SHOW_TOLERANCE = Duration.ofMinutes(15);

    @Transactional
    public AppointmentResponse markNoShow(Long userId, Long appointmentId) {
        User user = tenantAuthorizationService.requireEstablishmentStaff(userId);
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        requireOwningProfessionalOrAdministrator(user, appointment);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException("Apenas agendamentos confirmados podem ser marcados como falta.");
        }
        if (appointment.getStartsAt().plus(NO_SHOW_TOLERANCE).isAfter(LocalDateTime.now(clock))) {
            throw new ConflictException("A falta só pode ser registrada "
                    + NO_SHOW_TOLERANCE.toMinutes() + " minutos após o horário de início.");
        }
        appointment.markNoShow(userRepository.getReferenceById(userId), LocalDateTime.now(clock));
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
        appointment.invalidate(reason, administrator, LocalDateTime.now(clock));
        return AppointmentResponse.fromEntity(appointment);
    }

    private void requireOwningProfessionalOrAdministrator(User user, Appointment appointment) {
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
            Customer customer,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long ignoredAppointmentId,
            boolean enforceAdvanceWindow) {
        validateSlotAlignment(startsAt, enforceAdvanceWindow);
        if (enforceAdvanceWindow) validateAdvanceWindow(establishment, startsAt);
        else if (startsAt.isBefore(LocalDateTime.now(clock))) {
            throw new ConflictException("O agendamento não pode começar no passado.");
        }
        // O horário de funcionamento do estabelecimento já é considerado por
        // professionalAvailabilityService.validateAvailable(), que calcula a interseção entre
        // o expediente do salão e a jornada própria do profissional (quando houver). Isso evita
        // ter duas fontes independentes de verdade sobre o horário de atendimento.
        professionalAvailabilityService.validateAvailable(professional, startsAt, endsAt);
        validateProfessionalAvailability(professional.getId(), startsAt, endsAt, ignoredAppointmentId);
        lockCustomerUser(customer);
        validateClientAvailability(customer, startsAt, endsAt, ignoredAppointmentId);
    }

    private void validateSlotAlignment(LocalDateTime startsAt, boolean online) {
        int interval = online ? AppointmentSlotPolicy.SLOT_INTERVAL_MINUTES
                : AppointmentSlotPolicy.INTERNAL_SLOT_INTERVAL_MINUTES;
        if (!AppointmentSlotPolicy.isAligned(startsAt, interval)) {
            throw new ConflictException("O horário deve estar alinhado aos intervalos de " + interval + " minutos.");
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

    private void lockCustomerUser(Customer customer) {
        if (customer.getUser() != null) {
            userRepository.findByIdForUpdate(customer.getUser().getId());
        }
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
            Customer customer, LocalDateTime startsAt, LocalDateTime endsAt, Long ignoredAppointmentId) {
        Long clientId = customer.getUser() == null ? customer.getId() : customer.getUser().getId();
        boolean conflict = ignoredAppointmentId == null
                ? customer.getUser() == null
                        ? appointmentRepository.existsByCustomerIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                                clientId, AppointmentStatus.CONFIRMED, endsAt, startsAt)
                        : appointmentRepository.existsByCustomerUserIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                                clientId, AppointmentStatus.CONFIRMED, endsAt, startsAt)
                : customer.getUser() == null
                        ? appointmentRepository.existsByCustomerIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                                clientId, AppointmentStatus.CONFIRMED, ignoredAppointmentId, endsAt, startsAt)
                        : appointmentRepository.existsByCustomerUserIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
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
