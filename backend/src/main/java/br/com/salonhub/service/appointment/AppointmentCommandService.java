package br.com.salonhub.service.appointment;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TenantAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppointmentCommandService {

    private final AppointmentRepository appointmentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentBusinessHourRepository businessHourRepository;

    @Transactional
    public AppointmentResponse create(Long clientId, Long establishmentId, AppointmentCreateRequest request) {
        User client = lockClient(clientId);
        Establishment establishment = findActiveEstablishment(establishmentId);
        EstablishmentService service = findActiveService(request.serviceId(), establishmentId);
        User professional = findAndLockProfessional(request.professionalId(), establishmentId);
        validateProfessionalOffersService(professional, service);

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        validateSchedule(establishment, professional, client, startsAt, endsAt, null);

        Appointment appointment = Appointment.schedule(establishment, client, professional, service, startsAt);
        return AppointmentResponse.fromEntity(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse update(Long clientId, Long appointmentId, AppointmentCreateRequest request) {
        User client = lockClient(clientId);
        Appointment appointment = appointmentRepository.findByIdAndClientId(appointmentId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED
                || !appointment.getStartsAt().isAfter(LocalDateTime.now())) {
            throw new ConflictException("Apenas agendamentos futuros e confirmados podem ser editados.");
        }

        Long establishmentId = appointment.getEstablishment().getId();
        Establishment establishment = findActiveEstablishment(establishmentId);
        EstablishmentService service = findActiveService(request.serviceId(), establishmentId);
        User professional = findAndLockProfessional(request.professionalId(), establishmentId);
        validateProfessionalOffersService(professional, service);

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        validateSchedule(establishment, professional, client, startsAt, endsAt, appointment.getId());

        appointment.update(service, professional, startsAt);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long clientId, Long appointmentId) {
        User client = tenantAuthorizationService.requireClient(clientId);
        Appointment appointment = appointmentRepository.findByIdAndClientId(appointmentId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException("O agendamento não pode ser cancelado.");
        }
        appointment.cancel();
        return AppointmentResponse.fromEntity(appointment);
    }

    private void validateSchedule(
            Establishment establishment,
            User professional,
            User client,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long ignoredAppointmentId) {
        validateAdvanceWindow(establishment, startsAt);
        validateBusinessHours(establishment, startsAt, endsAt);
        validateProfessionalAvailability(professional.getId(), startsAt, endsAt, ignoredAppointmentId);
        validateClientAvailability(client.getId(), startsAt, endsAt, ignoredAppointmentId);
    }

    private Establishment findActiveEstablishment(Long establishmentId) {
        return establishmentRepository.findById(establishmentId)
                .filter(Establishment::isActive)
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

    private void validateProfessionalOffersService(User professional, EstablishmentService service) {
        if (!professional.isOffersAllServices()
                && professional.getOfferedServices().stream().noneMatch(item -> item.getId().equals(service.getId()))) {
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

    private void validateBusinessHours(
            Establishment establishment, LocalDateTime startsAt, LocalDateTime endsAt) {
        EstablishmentBusinessHour businessHour = requireOpenBusinessHour(establishment, startsAt.toLocalDate());
        boolean changesDay = !startsAt.toLocalDate().equals(endsAt.toLocalDate());
        boolean startsBeforeOpening = startsAt.toLocalTime().isBefore(businessHour.getOpeningTime());
        boolean endsAfterClosing = endsAt.toLocalTime().isAfter(businessHour.getClosingTime());
        if (changesDay || startsBeforeOpening || endsAfterClosing) {
            throw new ConflictException("O horário está fora do funcionamento do estabelecimento.");
        }
    }

    private void validateAdvanceWindow(Establishment establishment, LocalDateTime startsAt) {
        LocalDateTime now = LocalDateTime.now();
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

    private EstablishmentBusinessHour requireOpenBusinessHour(Establishment establishment, LocalDate date) {
        return businessHourRepository.findByEstablishmentIdAndDayOfWeek(establishment.getId(), date.getDayOfWeek())
                .filter(hour -> !hour.isClosed())
                .orElseThrow(() -> new ConflictException("O estabelecimento está fechado nesta data."));
    }
}
