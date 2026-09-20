package br.com.salonhub.service.appointment;

import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.dto.appointment.AvailabilityResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TenantAuthorizationService;
import br.com.salonhub.service.professionalagenda.ProfessionalAvailabilityService;
import br.com.salonhub.entity.ProfessionalCalendarEvent;
import br.com.salonhub.repository.ProfessionalCalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentQueryService {

    private final AppointmentRepository appointmentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final Clock clock;
    private final ProfessionalAvailabilityService professionalAvailabilityService;
    private final ProfessionalCalendarEventRepository calendarEventRepository;

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findMine(Long clientId) {
        User client = tenantAuthorizationService.requireClient(clientId);
        return appointmentRepository.findByClientIdOrderByStartsAtDesc(client.getId()).stream()
                .map(AppointmentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findMineAsProfessional(Long professionalId) {
        User professional = tenantAuthorizationService.requireProfessional(professionalId);
        return appointmentRepository.findByProfessionalIdOrderByStartsAtDesc(professional.getId()).stream()
                .map(AppointmentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findEstablishmentAgenda(
            Long administratorId, LocalDate date, Long professionalId, AppointmentStatus status) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        LocalDateTime dayStart = date == null ? null : date.atStartOfDay();
        LocalDateTime dayEnd = date == null ? null : date.plusDays(1).atStartOfDay();

        return appointmentRepository.findEstablishmentAgenda(
                        administrator.getEstablishment().getId(),
                        dayStart,
                        dayEnd,
                        professionalId,
                        status).stream()
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
        validateAppointmentOwnership(appointmentId, client);
        if (date.isBefore(LocalDate.now(clock))) {
            throw new InvalidRequestException("A data da consulta não pode estar no passado.");
        }

        Establishment establishment = establishmentRepository.findById(establishmentId)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime earliestStart = now.plusMinutes(establishment.getMinimumAdvanceMinutes());
        LocalDateTime latestStart = now.plusDays(establishment.getMaximumAdvanceDays());
        if (date.isAfter(latestStart.toLocalDate())) {
            throw new InvalidRequestException("A data excede a antecedência máxima permitida pelo estabelecimento.");
        }

        EstablishmentService service = serviceRepository
                .findByIdAndEstablishmentIdAndActiveTrue(serviceId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
        User professional = userRepository.findByIdAndEstablishmentIdAndRoleAndActiveTrue(
                        professionalId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
        requireProfessionalOffersService(professional, service);

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<Appointment> professionalAppointments = appointmentRepository
                .findByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalId, AppointmentStatus.CONFIRMED, dayEnd, dayStart);
        List<Appointment> clientAppointments = appointmentRepository
                .findByClientIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        client.getId(), AppointmentStatus.CONFIRMED, dayEnd, dayStart);
        List<ProfessionalCalendarEvent> calendarEvents = calendarEventRepository
                .findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalId, dayEnd, dayStart);

        ProfessionalAvailabilityService.WorkingHours workingHours =
                professionalAvailabilityService.requireWorkingHours(professional, date);
        List<LocalTime> availableTimes = calculateAvailableTimes(
                date, service, workingHours, professionalAppointments, clientAppointments, calendarEvents,
                earliestStart, latestStart, appointmentId);
        return new AvailabilityResponse(date, service.getDurationMinutes(), availableTimes);
    }

    private List<LocalTime> calculateAvailableTimes(
            LocalDate date,
            EstablishmentService service,
            ProfessionalAvailabilityService.WorkingHours workingHours,
            List<Appointment> professionalAppointments,
            List<Appointment> clientAppointments,
            List<ProfessionalCalendarEvent> calendarEvents,
            LocalDateTime earliestStart,
            LocalDateTime latestStart,
            Long ignoredAppointmentId) {
        List<LocalTime> availableTimes = new ArrayList<>();
        LocalDateTime closing = date.atTime(workingHours.closingTime());
        LocalDateTime candidate = alignToSlotGrid(date.atTime(workingHours.openingTime()));
        while (!candidate.plusMinutes(service.getDurationMinutes()).isAfter(closing)) {
            LocalDateTime slotStart = candidate;
            LocalDateTime slotEnd = slotStart.plusMinutes(service.getDurationMinutes());
            boolean withinAdvanceWindow = !slotStart.isBefore(earliestStart) && !slotStart.isAfter(latestStart);
            boolean professionalOverlaps = overlaps(professionalAppointments, slotStart, slotEnd, ignoredAppointmentId);
            boolean clientOverlaps = overlaps(clientAppointments, slotStart, slotEnd, ignoredAppointmentId);
            boolean calendarEventOverlaps = calendarEvents.stream().anyMatch(event ->
                    event.getStartsAt().isBefore(slotEnd) && event.getEndsAt().isAfter(slotStart));
            if (withinAdvanceWindow && !professionalOverlaps && !clientOverlaps && !calendarEventOverlaps) {
                availableTimes.add(slotStart.toLocalTime());
            }
            candidate = candidate.plusMinutes(AppointmentSlotPolicy.SLOT_INTERVAL_MINUTES);
        }
        return availableTimes;
    }

    /**
     * Garante que o primeiro horário candidato já esteja alinhado à grade de slots de
     * {@link AppointmentSlotPolicy#SLOT_INTERVAL_MINUTES} minutos, mesmo quando a jornada
     * (do estabelecimento ou do profissional) abre em um minuto que não é múltiplo do
     * intervalo. Sem isso, a disponibilidade podia oferecer horários (ex.: 09:15) que a
     * criação do agendamento rejeitava em seguida, por não estarem alinhados aos slots.
     */
    private LocalDateTime alignToSlotGrid(LocalDateTime time) {
        int slot = AppointmentSlotPolicy.SLOT_INTERVAL_MINUTES;
        LocalDateTime truncated = time.withSecond(0).withNano(0);
        int remainder = truncated.getMinute() % slot;
        return remainder == 0 ? truncated : truncated.plusMinutes(slot - remainder);
    }

    private boolean overlaps(
            List<Appointment> appointments,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long ignoredAppointmentId) {
        return appointments.stream()
                .filter(appointment -> ignoredAppointmentId == null
                        || !appointment.getId().equals(ignoredAppointmentId))
                .anyMatch(appointment -> appointment.getStartsAt().isBefore(endsAt)
                        && appointment.getEndsAt().isAfter(startsAt));
    }

    private void validateAppointmentOwnership(Long appointmentId, User client) {
        if (appointmentId != null) {
            appointmentRepository.findByIdAndClientId(appointmentId, client.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        }
    }

    private void requireProfessionalOffersService(User professional, EstablishmentService service) {
        if (!professional.offers(service)) {
            throw new ConflictException("O profissional selecionado não oferece este serviço.");
        }
    }

}
