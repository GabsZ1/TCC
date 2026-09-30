package br.com.salonhub.service.appointment;

import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.dto.appointment.AdministrativeCalendarBlockResponse;
import br.com.salonhub.dto.appointment.AdministrativeProfessionalAvailabilityResponse;
import br.com.salonhub.dto.appointment.AvailabilityResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CustomerRepository;
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
import org.springframework.security.access.AccessDeniedException;

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
    private final CustomerRepository customerRepository;
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
        return appointmentRepository.findByCustomerUserIdOrderByStartsAtDesc(client.getId()).stream()
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

    /**
     * Pendentes de fechamento: já terminaram e continuam CONFIRMED. O administrador vê os do
     * estabelecimento; o profissional, só os dele.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> findPendingClosure(Long userId) {
        User staff = tenantAuthorizationService.requireEstablishmentStaff(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<br.com.salonhub.entity.Appointment> pending = staff.getRole() == UserRole.ADMIN
                ? appointmentRepository.findByEstablishmentIdAndStatusAndEndsAtLessThanEqualOrderByStartsAt(
                        staff.getEstablishment().getId(), AppointmentStatus.CONFIRMED, now)
                : appointmentRepository.findByProfessionalIdAndStatusAndEndsAtLessThanEqualOrderByStartsAt(
                        staff.getId(), AppointmentStatus.CONFIRMED, now);
        return pending.stream().map(AppointmentResponse::fromEntity).toList();
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
    public List<AdministrativeProfessionalAvailabilityResponse> findTeamAvailability(
            Long administratorId, LocalDate date, Long professionalId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();
        List<User> professionals = userRepository.findByEstablishmentIdAndRoleAndActiveTrue(
                        establishmentId, UserRole.PROFESSIONAL).stream()
                .filter(professional -> professionalId == null || professional.getId().equals(professionalId))
                .toList();
        if (professionalId != null && professionals.isEmpty()) {
            throw new ResourceNotFoundException("Funcionário não encontrado.");
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        return professionals.stream().map(professional -> {
            List<AdministrativeCalendarBlockResponse> blocks = calendarEventRepository
                    .findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                            professional.getId(), dayEnd, dayStart).stream()
                    .map(AdministrativeCalendarBlockResponse::fromEntity)
                    .toList();
            try {
                ProfessionalAvailabilityService.WorkingHours hours =
                        professionalAvailabilityService.requireWorkingHours(professional, date);
                return new AdministrativeProfessionalAvailabilityResponse(
                        professional.getId(), professional.getName(), true,
                        hours.openingTime(), hours.closingTime(), blocks);
            } catch (ConflictException ignored) {
                return new AdministrativeProfessionalAvailabilityResponse(
                        professional.getId(), professional.getName(), false, null, null, blocks);
            }
        }).toList();
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
                .filter(Establishment::acceptsNewAppointments)
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
                .findByCustomerUserIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        client.getId(), AppointmentStatus.CONFIRMED, dayEnd, dayStart);
        List<ProfessionalCalendarEvent> calendarEvents = calendarEventRepository
                .findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalId, dayEnd, dayStart);

        ProfessionalAvailabilityService.WorkingHours workingHours =
                professionalAvailabilityService.requireWorkingHours(professional, date);
        List<LocalTime> availableTimes = calculateAvailableTimes(
                date, service.getDurationMinutes(), workingHours, professionalAppointments, clientAppointments, calendarEvents,
                earliestStart, latestStart, appointmentId, AppointmentSlotPolicy.SLOT_INTERVAL_MINUTES);
        return new AvailabilityResponse(date, service.getDurationMinutes(), availableTimes);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse findInternalAvailability(Long staffId, Long customerId, Long serviceId,
                                                        Long professionalId, LocalDate date, Long appointmentId) {
        User staff = tenantAuthorizationService.requireEstablishmentStaff(staffId);
        Long establishmentId = staff.getEstablishment().getId();
        Customer customer = customerRepository.findByIdAndEstablishmentId(customerId, establishmentId)
                .filter(Customer::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        Appointment current = null;
        if (appointmentId != null) {
            if (staff.getRole() != UserRole.ADMIN) throw new AccessDeniedException("Apenas o administrador pode consultar a remarcação.");
            current = appointmentRepository.findByIdAndEstablishmentId(appointmentId, establishmentId)
                    .filter(item -> item.getCustomer().getId().equals(customer.getId()))
                    .filter(item -> item.getStatus() == AppointmentStatus.CONFIRMED)
                    .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
            if (!current.getStartsAt().isAfter(LocalDateTime.now(clock)))
                throw new ConflictException("Apenas agendamentos futuros podem ser remarcados.");
        }
        if (date.isBefore(LocalDate.now(clock))) {
            throw new InvalidRequestException("A data da consulta não pode estar no passado.");
        }
        establishmentRepository.findById(establishmentId)
                .filter(Establishment::acceptsNewAppointments)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
        EstablishmentService service = serviceRepository.findByIdAndEstablishmentIdAndActiveTrue(serviceId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
        User professional = userRepository.findByIdAndEstablishmentIdAndRoleAndActiveTrue(
                        professionalId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
        if (staff.getRole() == UserRole.PROFESSIONAL && !staff.getId().equals(professionalId)) {
            throw new AccessDeniedException("O profissional só pode consultar a própria agenda.");
        }
        requireProfessionalOffersService(professional, service);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<Appointment> professionalAppointments = appointmentRepository
                .findByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalId, AppointmentStatus.CONFIRMED, dayEnd, dayStart);
        List<Appointment> customerAppointments = appointmentRepository
                .findByCustomerIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        customerId, AppointmentStatus.CONFIRMED, dayEnd, dayStart);
        List<ProfessionalCalendarEvent> blocks = calendarEventRepository
                .findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalId, dayEnd, dayStart);
        var hours = professionalAvailabilityService.requireWorkingHours(professional, date);
        int duration = current != null && current.getService().getId().equals(serviceId)
                ? current.getDurationMinutes() : service.getDurationMinutes();
        return new AvailabilityResponse(date, duration, calculateAvailableTimes(
                date, duration, hours, professionalAppointments, customerAppointments, blocks,
                LocalDateTime.now(clock), null, appointmentId, AppointmentSlotPolicy.INTERNAL_SLOT_INTERVAL_MINUTES));
    }

    private List<LocalTime> calculateAvailableTimes(
            LocalDate date,
            int durationMinutes,
            ProfessionalAvailabilityService.WorkingHours workingHours,
            List<Appointment> professionalAppointments,
            List<Appointment> clientAppointments,
            List<ProfessionalCalendarEvent> calendarEvents,
            LocalDateTime earliestStart,
            LocalDateTime latestStart,
            Long ignoredAppointmentId,
            int slotIntervalMinutes) {
        List<LocalTime> availableTimes = new ArrayList<>();
        LocalDateTime closing = date.atTime(workingHours.closingTime());
        LocalDateTime candidate = alignToSlotGrid(date.atTime(workingHours.openingTime()), slotIntervalMinutes);
        while (!candidate.plusMinutes(durationMinutes).isAfter(closing)) {
            LocalDateTime slotStart = candidate;
            LocalDateTime slotEnd = slotStart.plusMinutes(durationMinutes);
            boolean withinAdvanceWindow = !slotStart.isBefore(earliestStart)
                    && (latestStart == null || !slotStart.isAfter(latestStart));
            boolean professionalOverlaps = overlaps(professionalAppointments, slotStart, slotEnd, ignoredAppointmentId);
            boolean clientOverlaps = overlaps(clientAppointments, slotStart, slotEnd, ignoredAppointmentId);
            boolean calendarEventOverlaps = calendarEvents.stream().anyMatch(event ->
                    event.getStartsAt().isBefore(slotEnd) && event.getEndsAt().isAfter(slotStart));
            if (withinAdvanceWindow && !professionalOverlaps && !clientOverlaps && !calendarEventOverlaps) {
                availableTimes.add(slotStart.toLocalTime());
            }
            candidate = candidate.plusMinutes(slotIntervalMinutes);
        }
        return availableTimes;
    }

    /** Alinha o início da jornada à próxima posição da grade selecionada. */
    private LocalDateTime alignToSlotGrid(LocalDateTime time, int slot) {
        LocalDateTime truncated = time.withSecond(0).withNano(0);
        int remainder = truncated.getMinute() % slot;
        return remainder == 0 && time.equals(truncated)
                ? truncated : truncated.plusMinutes(slot - remainder);
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
            appointmentRepository.findByIdAndCustomerUserId(appointmentId, client.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
        }
    }

    private void requireProfessionalOffersService(User professional, EstablishmentService service) {
        if (!professional.offers(service)) {
            throw new ConflictException("O profissional selecionado não oferece este serviço.");
        }
    }

}
