package br.com.salonhub.service;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.dto.appointment.AvailabilityResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentBusinessHourRepository businessHourRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            EstablishmentRepository establishmentRepository,
            EstablishmentServiceRepository serviceRepository,
            UserRepository userRepository,
            TenantAuthorizationService tenantAuthorizationService,
            EstablishmentBusinessHourRepository businessHourRepository) {
        this.appointmentRepository = appointmentRepository;
        this.establishmentRepository = establishmentRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
        this.businessHourRepository = businessHourRepository;
    }

    @Transactional
    public AppointmentResponse create(Long clientId, Long establishmentId, AppointmentCreateRequest request) {
        User client = lockClient(clientId);
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));

        EstablishmentService service = serviceRepository
                .findByIdAndEstablishmentIdAndActiveTrue(request.serviceId(), establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));

        User professional = userRepository.findByIdForUpdate(request.professionalId())
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.PROFESSIONAL)
                .filter(user -> user.getEstablishment() != null)
                .filter(user -> user.getEstablishment().getId().equals(establishmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        validateAdvanceWindow(establishment, startsAt);
        validateBusinessHours(establishment, startsAt, endsAt);

        boolean conflict = appointmentRepository
                .existsByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                        professional.getId(),
                        AppointmentStatus.CONFIRMED,
                        endsAt,
                        startsAt
                );
        if (conflict) {
            throw new ConflictException("O funcionário já possui um agendamento nesse período.");
        }
        validateClientAvailability(client.getId(), startsAt, endsAt, null);

        Appointment appointment = Appointment.builder()
                .establishment(establishment)
                .client(client)
                .professional(professional)
                .service(service)
                .serviceName(service.getName())
                .agreedPrice(service.getPrice())
                .commissionPercentage(service.getCommissionPercentage())
                .commissionAmount(service.getPrice().multiply(service.getCommissionPercentage())
                        .divide(new java.math.BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP))
                .durationMinutes(service.getDurationMinutes())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        return AppointmentResponse.fromEntity(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse update(Long clientId, Long appointmentId, AppointmentCreateRequest request) {
        User client = lockClient(clientId);
        Appointment appointment = appointmentRepository.findByIdAndClientId(appointmentId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED || !appointment.getStartsAt().isAfter(LocalDateTime.now())) {
            throw new ConflictException("Apenas agendamentos futuros e confirmados podem ser editados.");
        }

        Long establishmentId = appointment.getEstablishment().getId();
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
        EstablishmentService service = serviceRepository
                .findByIdAndEstablishmentIdAndActiveTrue(request.serviceId(), establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
        User professional = userRepository.findByIdForUpdate(request.professionalId())
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.PROFESSIONAL)
                .filter(user -> user.getEstablishment() != null)
                .filter(user -> user.getEstablishment().getId().equals(establishmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        validateAdvanceWindow(establishment, startsAt);
        validateBusinessHours(establishment, startsAt, endsAt);

        boolean conflict = appointmentRepository
                .existsByProfessionalIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        professional.getId(),
                        AppointmentStatus.CONFIRMED,
                        appointment.getId(),
                        endsAt,
                        startsAt
                );
        if (conflict) {
            throw new ConflictException("O funcionário já possui um agendamento nesse período.");
        }
        validateClientAvailability(client.getId(), startsAt, endsAt, appointment.getId());

        appointment.update(service, professional, startsAt, endsAt);
        return AppointmentResponse.fromEntity(appointmentRepository.save(appointment));
    }
    @Transactional(readOnly = true)
    public List<AppointmentResponse> findMine(Long clientId) {
        User client = tenantAuthorizationService.requireClient(clientId);
        return appointmentRepository.findByClientIdOrderByStartsAtDesc(client.getId())
                .stream()
                .map(AppointmentResponse::fromEntity)
                .toList();
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
        return AppointmentResponse.fromEntity(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findMineAsProfessional(Long professionalId) {
        User professional = tenantAuthorizationService.requireProfessional(professionalId);
        return appointmentRepository.findByProfessionalIdOrderByStartsAtDesc(professional.getId())
                .stream()
                .map(AppointmentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse findAvailability(
            Long clientId,
            Long establishmentId,
            Long serviceId,
            Long professionalId,
            LocalDate date,
            Long appointmentId) {
        User client = tenantAuthorizationService.requireClient(clientId);
        if (appointmentId != null) {
            appointmentRepository.findByIdAndClientId(appointmentId, client.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        }
        if (date.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("A data da consulta não pode estar no passado.");
        }

        Establishment establishment = establishmentRepository.findById(establishmentId)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliestStart = now.plusMinutes(establishment.getMinimumAdvanceMinutes());
        LocalDateTime latestStart = now.plusDays(establishment.getMaximumAdvanceDays());
        if (date.isAfter(latestStart.toLocalDate())) {
            throw new InvalidRequestException("A data excede a antecedência máxima permitida pelo estabelecimento.");
        }
        EstablishmentService service = serviceRepository
                .findByIdAndEstablishmentIdAndActiveTrue(serviceId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
        userRepository.findByIdAndEstablishmentIdAndRoleAndActiveTrue(
                        professionalId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<Appointment> appointments = appointmentRepository
                .findByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalId,
                        AppointmentStatus.CONFIRMED,
                        dayEnd,
                        dayStart
                );
        List<Appointment> clientAppointments = appointmentRepository
                .findByClientIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        client.getId(),
                        AppointmentStatus.CONFIRMED,
                        dayEnd,
                        dayStart
                );

        List<LocalTime> availableTimes = new ArrayList<>();
        EstablishmentBusinessHour businessHour = requireOpenBusinessHour(establishment, date);
        LocalDateTime candidate = date.atTime(businessHour.getOpeningTime());
        LocalDateTime closing = date.atTime(businessHour.getClosingTime());
        while (!candidate.plusMinutes(service.getDurationMinutes()).isAfter(closing)) {
            LocalDateTime slotStart = candidate;
            LocalDateTime candidateEnd = slotStart.plusMinutes(service.getDurationMinutes());
            boolean withinAdvanceWindow = !slotStart.isBefore(earliestStart) && !slotStart.isAfter(latestStart);
            boolean overlaps = appointments.stream()
                    .filter(appointment -> appointmentId == null || !appointment.getId().equals(appointmentId))
                    .anyMatch(appointment -> appointment.getStartsAt().isBefore(candidateEnd)
                            && appointment.getEndsAt().isAfter(slotStart));
            boolean clientOverlaps = clientAppointments.stream()
                    .filter(appointment -> appointmentId == null || !appointment.getId().equals(appointmentId))
                    .anyMatch(appointment -> appointment.getStartsAt().isBefore(candidateEnd)
                            && appointment.getEndsAt().isAfter(slotStart));
            if (withinAdvanceWindow && !overlaps && !clientOverlaps) {
                availableTimes.add(slotStart.toLocalTime());
            }
            candidate = candidate.plusMinutes(30);
        }

        return new AvailabilityResponse(date, service.getDurationMinutes(), availableTimes);
    }

    private User lockClient(Long clientId) {
        tenantAuthorizationService.requireClient(clientId);
        return userRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
    }

    private void validateClientAvailability(
            Long clientId,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long ignoredAppointmentId) {
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
            Establishment establishment,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
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
