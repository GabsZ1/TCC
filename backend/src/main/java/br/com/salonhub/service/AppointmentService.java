package br.com.salonhub.service;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.dto.appointment.AvailabilityResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
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

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            EstablishmentRepository establishmentRepository,
            EstablishmentServiceRepository serviceRepository,
            UserRepository userRepository,
            TenantAuthorizationService tenantAuthorizationService) {
        this.appointmentRepository = appointmentRepository;
        this.establishmentRepository = establishmentRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Transactional
    public AppointmentResponse create(Long clientId, Long establishmentId, AppointmentCreateRequest request) {
        User client = tenantAuthorizationService.requireClient(clientId);
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

        Appointment appointment = Appointment.builder()
                .establishment(establishment)
                .client(client)
                .professional(professional)
                .service(service)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(AppointmentStatus.CONFIRMED)
                .build();

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

    @Transactional(readOnly = true)
    public AvailabilityResponse findAvailability(
            Long clientId,
            Long establishmentId,
            Long serviceId,
            Long professionalId,
            LocalDate date) {
        tenantAuthorizationService.requireClient(clientId);
        if (date.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("A data da consulta não pode estar no passado.");
        }

        Establishment establishment = establishmentRepository.findById(establishmentId)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
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

        List<LocalTime> availableTimes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime candidate = date.atTime(establishment.getOpeningTime());
        LocalDateTime closing = date.atTime(establishment.getClosingTime());
        while (!candidate.plusMinutes(service.getDurationMinutes()).isAfter(closing)) {
            LocalDateTime slotStart = candidate;
            LocalDateTime candidateEnd = slotStart.plusMinutes(service.getDurationMinutes());
            boolean inFuture = slotStart.isAfter(now);
            boolean overlaps = appointments.stream().anyMatch(appointment ->
                    appointment.getStartsAt().isBefore(candidateEnd)
                            && appointment.getEndsAt().isAfter(slotStart));
            if (inFuture && !overlaps) {
                availableTimes.add(slotStart.toLocalTime());
            }
            candidate = candidate.plusMinutes(30);
        }

        return new AvailabilityResponse(date, service.getDurationMinutes(), availableTimes);
    }

    private void validateBusinessHours(
            Establishment establishment,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
        boolean changesDay = !startsAt.toLocalDate().equals(endsAt.toLocalDate());
        boolean startsBeforeOpening = startsAt.toLocalTime().isBefore(establishment.getOpeningTime());
        boolean endsAfterClosing = endsAt.toLocalTime().isAfter(establishment.getClosingTime());

        if (changesDay || startsBeforeOpening || endsAfterClosing) {
            throw new ConflictException("O horário está fora do funcionamento do estabelecimento.");
        }
    }
}
