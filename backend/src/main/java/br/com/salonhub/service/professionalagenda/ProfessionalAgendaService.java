package br.com.salonhub.service.professionalagenda;

import br.com.salonhub.dto.professionalagenda.*;
import br.com.salonhub.entity.*;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.*;
import br.com.salonhub.service.TenantAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProfessionalAgendaService {

    private static final int MAX_QUERY_DAYS = 366;

    private final TenantAuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final EstablishmentBusinessHourRepository establishmentHourRepository;
    private final ProfessionalBusinessHourRepository professionalHourRepository;
    private final ProfessionalCalendarEventRepository eventRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ProfessionalHourResponse> findSchedule(Long professionalId) {
        User professional = requireProfessionalWithEstablishment(professionalId);
        Map<DayOfWeek, ProfessionalBusinessHour> ownHours = professionalHourRepository
                .findByProfessionalIdOrderByDayOfWeek(professional.getId()).stream()
                .collect(Collectors.toMap(ProfessionalBusinessHour::getDayOfWeek, Function.identity()));
        Map<DayOfWeek, EstablishmentBusinessHour> establishmentHours = establishmentHourRepository
                .findByEstablishmentIdOrderByDayOfWeek(professional.getEstablishment().getId()).stream()
                .collect(Collectors.toMap(EstablishmentBusinessHour::getDayOfWeek, Function.identity()));

        return Arrays.stream(DayOfWeek.values()).map(day -> {
            EstablishmentBusinessHour establishmentHour = establishmentHours.get(day);
            boolean establishmentClosed = establishmentHour == null || establishmentHour.isClosed();
            LocalTime establishmentOpeningTime = establishmentClosed ? null : establishmentHour.getOpeningTime();
            LocalTime establishmentClosingTime = establishmentClosed ? null : establishmentHour.getClosingTime();

            ProfessionalBusinessHour own = ownHours.get(day);
            if (own != null) {
                return new ProfessionalHourResponse(day, own.isClosed(), own.getOpeningTime(), own.getClosingTime(),
                        false, establishmentClosed, establishmentOpeningTime, establishmentClosingTime);
            }
            return new ProfessionalHourResponse(day, establishmentClosed, establishmentOpeningTime, establishmentClosingTime,
                    true, establishmentClosed, establishmentOpeningTime, establishmentClosingTime);
        }).toList();
    }

    @Transactional
    public List<ProfessionalHourResponse> updateSchedule(
            Long professionalId, ProfessionalScheduleUpdateRequest request) {
        User professional = lockProfessional(professionalId);
        validateCompleteWeek(request.businessHours());
        Map<DayOfWeek, EstablishmentBusinessHour> establishmentHours = establishmentHourRepository
                .findByEstablishmentIdOrderByDayOfWeek(professional.getEstablishment().getId()).stream()
                .collect(Collectors.toMap(EstablishmentBusinessHour::getDayOfWeek, Function.identity()));
        Map<DayOfWeek, ProfessionalBusinessHour> current = professionalHourRepository
                .findByProfessionalIdOrderByDayOfWeek(professional.getId()).stream()
                .collect(Collectors.toMap(ProfessionalBusinessHour::getDayOfWeek, Function.identity()));
        Map<DayOfWeek, ProfessionalHourRequest> requestByDay = request.businessHours().stream()
                .collect(Collectors.toMap(ProfessionalHourRequest::dayOfWeek, Function.identity()));

        for (ProfessionalHourRequest hour : request.businessHours()) {
            if (!hour.useEstablishmentHours()) {
                validateWithinEstablishment(hour, establishmentHours.get(hour.dayOfWeek()));
            }
        }
        requireNoConflictingFutureAppointments(professional, establishmentHours, requestByDay);

        // Modelo de override: só existe ProfessionalBusinessHour quando o dia é realmente
        // personalizado. Quando o funcionário opta por "usar horário do salão", qualquer
        // override existente é removido e o dia volta a herdar o horário do estabelecimento
        // automaticamente — inclusive em mudanças futuras feitas pelo administrador.
        for (ProfessionalHourRequest hour : request.businessHours()) {
            ProfessionalBusinessHour entity = current.get(hour.dayOfWeek());
            if (hour.useEstablishmentHours()) {
                if (entity != null) {
                    professionalHourRepository.delete(entity);
                }
                continue;
            }
            if (entity == null) {
                entity = ProfessionalBusinessHour.create(professional, hour.dayOfWeek(), hour.closed(),
                        hour.openingTime(), hour.closingTime());
                professionalHourRepository.save(entity);
            } else {
                entity.update(hour.closed(), hour.openingTime(), hour.closingTime());
            }
        }
        return findSchedule(professionalId);
    }

    /**
     * Impede salvar uma jornada que tornaria agendamentos já confirmados (futuros ou em
     * andamento) incompatíveis com o novo horário (ex.: fechar um dia ou reduzir o expediente
     * exatamente no horário de um atendimento já marcado ou que já esteja em curso). Usa o
     * término do agendamento (não o início) como corte, para cobrir também um atendimento que
     * já começou mas ainda não terminou — mesmo critério usado em EmployeeService.deactivate()
     * e EstablishmentManagementService.deactivate(). Sem essa checagem, a agenda ficava
     * contraditória: um atendimento CONFIRMED existindo num horário em que o profissional
     * formalmente "não trabalha mais".
     */
    private void requireNoConflictingFutureAppointments(
            User professional,
            Map<DayOfWeek, EstablishmentBusinessHour> establishmentHours,
            Map<DayOfWeek, ProfessionalHourRequest> requestByDay) {
        List<Appointment> relevantConfirmed = appointmentRepository
                .findByProfessionalIdAndStatusAndEndsAtGreaterThanOrderByStartsAt(
                        professional.getId(), AppointmentStatus.CONFIRMED, LocalDateTime.now(clock));
        if (relevantConfirmed.isEmpty()) {
            return;
        }
        long conflicting = relevantConfirmed.stream()
                .filter(appointment -> fallsOutsideNewSchedule(appointment, establishmentHours, requestByDay))
                .count();
        if (conflicting > 0) {
            throw new ConflictException(
                    "Não foi possível alterar a jornada: " + conflicting
                            + " agendamento(s) confirmado(s) futuro(s) ou em andamento ficariam fora do novo horário. "
                            + "Cancele-os, aguarde a conclusão ou ajuste a jornada para mantê-los dentro do expediente.");
        }
    }

    private boolean fallsOutsideNewSchedule(
            Appointment appointment,
            Map<DayOfWeek, EstablishmentBusinessHour> establishmentHours,
            Map<DayOfWeek, ProfessionalHourRequest> requestByDay) {
        DayOfWeek day = appointment.getStartsAt().getDayOfWeek();
        ProfessionalHourRequest hour = requestByDay.get(day);
        if (hour == null) {
            return false;
        }
        boolean closed;
        LocalTime opening;
        LocalTime closing;
        if (hour.useEstablishmentHours()) {
            EstablishmentBusinessHour establishmentHour = establishmentHours.get(day);
            closed = establishmentHour == null || establishmentHour.isClosed();
            opening = closed ? null : establishmentHour.getOpeningTime();
            closing = closed ? null : establishmentHour.getClosingTime();
        } else {
            closed = hour.closed();
            opening = hour.openingTime();
            closing = hour.closingTime();
        }
        if (closed) {
            return true;
        }
        boolean changesDay = !appointment.getStartsAt().toLocalDate().equals(appointment.getEndsAt().toLocalDate());
        return changesDay
                || appointment.getStartsAt().toLocalTime().isBefore(opening)
                || appointment.getEndsAt().toLocalTime().isAfter(closing);
    }

    @Transactional
    public CalendarEventResponse createEvent(Long professionalId, CalendarEventRequest request) {
        User professional = lockProfessional(professionalId);
        validateEvent(request, professional.getId(), null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!request.endsAt().isAfter(now)) {
            throw new InvalidRequestException("Não é possível criar um compromisso que já terminou.");
        }
        ProfessionalCalendarEvent event = ProfessionalCalendarEvent.create(
                professional.getEstablishment(), professional, request.title(), request.description(),
                request.startsAt(), request.endsAt(), request.allDay());
        return CalendarEventResponse.fromEntity(eventRepository.save(event));
    }

    @Transactional
    public CalendarEventResponse updateEvent(Long professionalId, Long eventId, CalendarEventRequest request) {
        User professional = lockProfessional(professionalId);
        ProfessionalCalendarEvent event = eventRepository.findByIdAndProfessionalId(eventId, professional.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validateEvent(request, professional.getId(), event.getId());
        event.update(request.title(), request.description(), request.startsAt(), request.endsAt(), request.allDay());
        return CalendarEventResponse.fromEntity(event);
    }

    @Transactional
    public void deleteEvent(Long professionalId, Long eventId) {
        User professional = requireActiveProfessionalWithEstablishment(professionalId);
        ProfessionalCalendarEvent event = eventRepository.findByIdAndProfessionalId(eventId, professional.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<ProfessionalAgendaItemResponse> findAgenda(
            Long professionalId, LocalDateTime startsAt, LocalDateTime endsAt) {
        User professional = requireProfessionalWithEstablishment(professionalId);
        validatePeriod(startsAt, endsAt);
        Stream<ProfessionalAgendaItemResponse> appointments = appointmentRepository
                .findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professional.getId(), endsAt, startsAt).stream()
                .map(ProfessionalAgendaItemResponse::fromAppointment);
        Stream<ProfessionalAgendaItemResponse> events = eventRepository
                .findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                        professional.getId(), endsAt, startsAt).stream()
                .map(ProfessionalAgendaItemResponse::fromEvent);
        return Stream.concat(appointments, events)
                .sorted(Comparator.comparing(ProfessionalAgendaItemResponse::startsAt))
                .toList();
    }

    private void validateCompleteWeek(List<ProfessionalHourRequest> hours) {
        Set<DayOfWeek> days = hours.stream().map(ProfessionalHourRequest::dayOfWeek)
                .collect(Collectors.toSet());
        if (days.size() != DayOfWeek.values().length) {
            throw new InvalidRequestException("Informe exatamente um horário para cada dia da semana.");
        }
    }

    private void validateWithinEstablishment(
            ProfessionalHourRequest hour, EstablishmentBusinessHour establishmentHour) {
        if (hour.closed()) {
            if (hour.openingTime() != null || hour.closingTime() != null) {
                throw new InvalidRequestException("Dias fechados não podem possuir horários.");
            }
            return;
        }
        if (hour.openingTime() == null || hour.closingTime() == null
                || !hour.openingTime().isBefore(hour.closingTime())) {
            throw new InvalidRequestException("O horário inicial deve ser anterior ao horário final.");
        }
        if (establishmentHour == null || establishmentHour.isClosed()) {
            throw new ConflictException("O profissional não pode trabalhar em um dia em que o estabelecimento está fechado.");
        }
        if (hour.openingTime().isBefore(establishmentHour.getOpeningTime())
                || hour.closingTime().isAfter(establishmentHour.getClosingTime())) {
            throw new ConflictException("O horário do profissional deve estar dentro do expediente do estabelecimento.");
        }
    }

    private void validateEvent(CalendarEventRequest request, Long professionalId, Long ignoredEventId) {
        validatePeriod(request.startsAt(), request.endsAt());
        if (request.allDay() && (!request.startsAt().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                || !request.endsAt().toLocalTime().equals(java.time.LocalTime.MIDNIGHT))) {
            throw new InvalidRequestException("Eventos de dia inteiro devem começar e terminar à meia-noite.");
        }
        boolean eventConflict = ignoredEventId == null
                ? eventRepository.existsByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThan(
                        professionalId, request.endsAt(), request.startsAt())
                : eventRepository.existsByProfessionalIdAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        professionalId, ignoredEventId, request.endsAt(), request.startsAt());
        if (eventConflict) {
            throw new ConflictException("Já existe um evento pessoal nesse período.");
        }
        if (appointmentRepository.existsByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                professionalId, AppointmentStatus.CONFIRMED, request.endsAt(), request.startsAt())) {
            throw new ConflictException("Já existe um agendamento confirmado nesse período.");
        }
    }

    private void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new InvalidRequestException("O início deve ser anterior ao término.");
        }
        if (startsAt.plusDays(MAX_QUERY_DAYS).isBefore(endsAt)) {
            throw new InvalidRequestException("O período não pode ultrapassar 366 dias.");
        }
    }

    private User lockProfessional(Long professionalId) {
        authorizationService.requireProfessionalAtActiveEstablishment(professionalId);
        return userRepository.findByIdForUpdate(professionalId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.PROFESSIONAL && user.getEstablishment() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado."));
    }

    private User requireActiveProfessionalWithEstablishment(Long professionalId) {
        User professional = authorizationService.requireProfessionalAtActiveEstablishment(professionalId);
        if (professional.getEstablishment() == null) {
            throw new ResourceNotFoundException("Profissional não possui estabelecimento vinculado.");
        }
        return professional;
    }
    private User requireProfessionalWithEstablishment(Long professionalId) {
        User professional = authorizationService.requireProfessional(professionalId);
        if (professional.getEstablishment() == null) {
            throw new ResourceNotFoundException("Profissional não possui estabelecimento vinculado.");
        }
        return professional;
    }
}
