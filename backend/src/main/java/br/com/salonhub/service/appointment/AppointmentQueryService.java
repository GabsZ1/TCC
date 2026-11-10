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
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.service.professionalagenda.ProfessionalAvailabilityService;
import br.com.salonhub.entity.ProfessionalCalendarEvent;
import br.com.salonhub.entity.EstablishmentCalendarBlock;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.ProfessionalBusinessHour;
import br.com.salonhub.repository.ProfessionalCalendarEventRepository;
import br.com.salonhub.repository.EstablishmentCalendarBlockRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.ProfessionalBusinessHourRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Consulta agendamentos dentro do escopo permitido a cada papel. */
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
    private final EstablishmentCalendarBlockRepository establishmentBlockRepository;
    private final ProfessionalBusinessHourRepository professionalHourRepository;
    private final EstablishmentBusinessHourRepository establishmentHourRepository;

    /** Lista as reservas vinculadas à conta do cliente, inclusive as já encerradas. */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findMine(Long clientId, int page, int size) {
        User client = tenantAuthorizationService.requireClient(clientId);
        return appointmentRepository.findByCustomerUserIdOrderByStartsAtDescIdDesc(
                client.getId(), PageRequest.of(page, size)).map(AppointmentResponse::fromEntity);
    }

    /** Busca a próxima reserva confirmada que ainda não começou. */
    @Transactional(readOnly = true)
    public AppointmentResponse findNextForClient(Long clientId) {
        User client = tenantAuthorizationService.requireClient(clientId);
        return appointmentRepository.findFirstByCustomerUserIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(
                client.getId(), AppointmentStatus.CONFIRMED, LocalDateTime.now(clock))
                .map(AppointmentResponse::fromEntity).orElse(null);
    }

    /** Separa próximas reservas e histórico do cliente antes de aplicar a paginação. */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findMinePage(Long clientId, boolean history, int page, int size) {
        User client = tenantAuthorizationService.requireClient(clientId);
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Appointment> result = history
                ? appointmentRepository.findClientHistory(client.getId(), now, PageRequest.of(page, size))
                : appointmentRepository.findByCustomerUserIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(
                        client.getId(), AppointmentStatus.CONFIRMED, now, PageRequest.of(page, size));
        return result.map(AppointmentResponse::fromEntity);
    }

    public record ProfessionalToday(long confirmedCount, AppointmentResponse next) {}

    /** Resume os atendimentos confirmados de hoje e o próximo ainda em curso ou por iniciar. */
    @Transactional(readOnly = true)
    public ProfessionalToday findTodayForProfessional(Long professionalId) {
        User professional = tenantAuthorizationService.requireProfessional(professionalId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime start = now.toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long count = appointmentRepository.countByProfessionalIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                professional.getId(), AppointmentStatus.CONFIRMED, start, end);
        AppointmentResponse next = appointmentRepository
                .findFirstByProfessionalIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThanOrderByStartsAtAscIdAsc(
                        professional.getId(), AppointmentStatus.CONFIRMED, now, end)
                .map(AppointmentResponse::fromEntity).orElse(null);
        return new ProfessionalToday(count, next);
    }

    /** Lista o histórico de reservas atribuídas ao profissional autenticado. */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findMineAsProfessional(Long professionalId, int page, int size) {
        User professional = tenantAuthorizationService.requireProfessional(professionalId);
        return appointmentRepository.findByProfessionalIdOrderByStartsAtDescIdDesc(
                professional.getId(), PageRequest.of(page, size)).map(AppointmentResponse::fromEntity);
    }

    /**
     * Pendentes de fechamento: já terminaram e continuam CONFIRMED. O administrador vê os do
     * estabelecimento; o profissional, só os dele.
     */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findPendingClosure(Long userId, int page, int size) {
        User staff = tenantAuthorizationService.requireEstablishmentStaff(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Appointment> pending = staff.getRole() == UserRole.ADMIN
                ? appointmentRepository.findByEstablishmentIdAndStatusAndEndsAtLessThanEqualOrderByStartsAtAscIdAsc(
                        staff.getEstablishment().getId(), AppointmentStatus.CONFIRMED, now, PageRequest.of(page, size))
                : appointmentRepository.findByProfessionalIdAndStatusAndEndsAtLessThanEqualOrderByStartsAtAscIdAsc(
                        staff.getId(), AppointmentStatus.CONFIRMED, now, PageRequest.of(page, size));
        return pending.map(AppointmentResponse::fromEntity);
    }

    /** Consulta a agenda do estabelecimento respeitando filtros e escopo administrativo. */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findEstablishmentAgenda(
            Long administratorId, LocalDate date, Long professionalId, AppointmentStatus status, int page, int size) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        LocalDateTime dayStart = date == null ? null : date.atStartOfDay();
        LocalDateTime dayEnd = date == null ? null : date.plusDays(1).atStartOfDay();

        return appointmentRepository.findEstablishmentAgenda(
                        administrator.getEstablishment().getId(),
                        dayStart,
                        dayEnd,
                        professionalId,
                        status, PageRequest.of(page, size)).map(AppointmentResponse::fromEntity);
    }

    /** Monta a disponibilidade da equipe para a visão administrativa da agenda. */
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
        if (professionals.isEmpty()) {
            return List.of();
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<Long> professionalIds = professionals.stream().map(User::getId).toList();
        Map<Long, List<ProfessionalCalendarEvent>> blocksByProfessional = calendarEventRepository
                .findByProfessionalIdInAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalIds, dayEnd, dayStart)
                .stream().collect(Collectors.groupingBy(event -> event.getProfessional().getId()));
        Map<Long, ProfessionalBusinessHour> hoursByProfessional = professionalHourRepository
                .findByProfessionalIdInAndDayOfWeek(professionalIds, date.getDayOfWeek())
                .stream().collect(Collectors.toMap(hour -> hour.getProfessional().getId(), Function.identity()));
        EstablishmentBusinessHour establishmentHour = establishmentHourRepository
                .findByEstablishmentIdAndDayOfWeek(establishmentId, date.getDayOfWeek())
                .orElse(null);
        return professionals.stream().map(professional -> {
            List<AdministrativeCalendarBlockResponse> blocks = blocksByProfessional
                    .getOrDefault(professional.getId(), List.of()).stream()
                    .map(AdministrativeCalendarBlockResponse::fromEntity)
                    .toList();
            try {
                ProfessionalAvailabilityService.WorkingHours hours =
                        professionalAvailabilityService.requireWorkingHours(
                                establishmentHour, hoursByProfessional.get(professional.getId()));
                return new AdministrativeProfessionalAvailabilityResponse(
                        professional.getId(), professional.getName(), true,
                        hours.openingTime(), hours.closingTime(), blocks);
            } catch (ConflictException ignored) {
                return new AdministrativeProfessionalAvailabilityResponse(
                        professional.getId(), professional.getName(), false, null, null, blocks);
            }
        }).toList();
    }

    /** Calcula horários de reserva online considerando serviço, profissional e regras de antecedência. */
    @Transactional(readOnly = true)
    public AvailabilityResponse findAvailability(
            Long clientId,
            Long establishmentId,
            Long serviceId,
            Long professionalId,
            LocalDate date,
            Long appointmentId) {
        User client = tenantAuthorizationService.requireClient(clientId);
        Appointment current = validateAppointmentOwnership(appointmentId, client);
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

        int durationMinutes = current != null && current.getService().getId().equals(serviceId)
                ? current.getDurationMinutes() : service.getDurationMinutes();
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
        List<EstablishmentCalendarBlock> establishmentBlocks = establishmentBlockRepository
                .findByEstablishmentIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        establishmentId, dayEnd, dayStart);

        ProfessionalAvailabilityService.WorkingHours workingHours =
                professionalAvailabilityService.requireWorkingHours(professional, date);
        List<LocalTime> availableTimes = calculateAvailableTimes(
                date, durationMinutes, workingHours, professionalAppointments, clientAppointments, calendarEvents, establishmentBlocks,
                earliestStart, latestStart, appointmentId, AppointmentSlotPolicy.SLOT_INTERVAL_MINUTES);
        return new AvailabilityResponse(date, durationMinutes, availableTimes);
    }

    /** Calcula horários para a equipe com as regras próprias do agendamento interno. */
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
        List<Appointment> customerAppointments = customer.getUser() == null
                ? appointmentRepository.findByCustomerIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        customerId, AppointmentStatus.CONFIRMED, dayEnd, dayStart)
                : appointmentRepository.findByCustomerUserIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        customer.getUser().getId(), AppointmentStatus.CONFIRMED, dayEnd, dayStart);
        List<ProfessionalCalendarEvent> blocks = calendarEventRepository
                .findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professionalId, dayEnd, dayStart);
        List<EstablishmentCalendarBlock> establishmentBlocks = establishmentBlockRepository
                .findByEstablishmentIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        establishmentId, dayEnd, dayStart);
        var hours = professionalAvailabilityService.requireWorkingHours(professional, date);
        int duration = current != null && current.getService().getId().equals(serviceId)
                ? current.getDurationMinutes() : service.getDurationMinutes();
        return new AvailabilityResponse(date, duration, calculateAvailableTimes(
                date, duration, hours, professionalAppointments, customerAppointments, blocks, establishmentBlocks,
                LocalDateTime.now(clock), null, appointmentId, AppointmentSlotPolicy.INTERNAL_SLOT_INTERVAL_MINUTES));
    }

    /**
     * Percorre a grade do expediente e aceita apenas horários dentro da antecedência
     * e sem sobreposição com reservas, eventos ou bloqueios.
     */
    private List<LocalTime> calculateAvailableTimes(
            LocalDate date,
            int durationMinutes,
            ProfessionalAvailabilityService.WorkingHours workingHours,
            List<Appointment> professionalAppointments,
            List<Appointment> clientAppointments,
            List<ProfessionalCalendarEvent> calendarEvents,
            List<EstablishmentCalendarBlock> establishmentBlocks,
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
            boolean establishmentBlockOverlaps = establishmentBlocks.stream().anyMatch(block ->
                    block.getStartsAt().isBefore(slotEnd) && block.getEndsAt().isAfter(slotStart));
            if (withinAdvanceWindow && !professionalOverlaps && !clientOverlaps && !calendarEventOverlaps && !establishmentBlockOverlaps) {
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

    private Appointment validateAppointmentOwnership(Long appointmentId, User client) {
        if (appointmentId == null) return null;
        return appointmentRepository.findByIdAndCustomerUserId(appointmentId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado."));
    }

    private void requireProfessionalOffersService(User professional, EstablishmentService service) {
        if (!professional.offers(service)) {
            throw new ConflictException("O profissional selecionado não oferece este serviço.");
        }
    }

}
