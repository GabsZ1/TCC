package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.professionalagenda.*;
import br.com.salonhub.service.professionalagenda.ProfessionalAgendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

/** Expõe as operações HTTP de agenda e atuação dos profissionais e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.PROFESSIONAL)
@RestController
@RequestMapping("/api/profissionais/me/agenda")
@RequiredArgsConstructor
public class ProfessionalAgendaController {

    private final ProfessionalAgendaService agendaService;
    private final Clock clock;

    /** Consulta os dados necessários para configurar a própria agenda. */
    @GetMapping("/configuracao")
    public ProfessionalAgendaConfigurationResponse configuration(@AuthenticationPrincipal Jwt jwt) {
        agendaService.findSchedule(currentUserId(jwt));
        return new ProfessionalAgendaConfigurationResponse(clock.getZone().getId());
    }

    /** Consulta a jornada semanal do profissional da sessão. */
    @GetMapping("/horarios")
    public List<ProfessionalHourResponse> findSchedule(@AuthenticationPrincipal Jwt jwt) {
        return agendaService.findSchedule(currentUserId(jwt));
    }

    /** Substitui a jornada semanal do profissional autenticado. */
    @PutMapping("/horarios")
    public List<ProfessionalHourResponse> updateSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfessionalScheduleUpdateRequest request) {
        return agendaService.updateSchedule(currentUserId(jwt), request);
    }

    /** Bloqueia um intervalo na própria agenda profissional. */
    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse createEvent(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CalendarEventRequest request) {
        return agendaService.createEvent(currentUserId(jwt), request);
    }

    /** Altera um evento da própria agenda profissional. */
    @PutMapping("/eventos/{eventId}")
    public CalendarEventResponse updateEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long eventId,
            @Valid @RequestBody CalendarEventRequest request) {
        return agendaService.updateEvent(currentUserId(jwt), eventId, request);
    }

    /** Remove um evento da própria agenda profissional. */
    @DeleteMapping("/eventos/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        agendaService.deleteEvent(currentUserId(jwt), eventId);
    }

    /** Lista eventos e atendimentos do profissional no período solicitado. */
    @GetMapping
    public List<ProfessionalAgendaItemResponse> findAgenda(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt) {
        return agendaService.findAgenda(currentUserId(jwt), startsAt, endsAt);
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
