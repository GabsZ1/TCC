package br.com.salonhub.controller;

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
import java.util.List;

@RestController
@RequestMapping("/api/profissionais/me/agenda")
@RequiredArgsConstructor
public class ProfessionalAgendaController {

    private final ProfessionalAgendaService agendaService;

    @GetMapping("/horarios")
    public List<ProfessionalHourResponse> findSchedule(@AuthenticationPrincipal Jwt jwt) {
        return agendaService.findSchedule(currentUserId(jwt));
    }

    @PutMapping("/horarios")
    public List<ProfessionalHourResponse> updateSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfessionalScheduleUpdateRequest request) {
        return agendaService.updateSchedule(currentUserId(jwt), request);
    }

    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse createEvent(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CalendarEventRequest request) {
        return agendaService.createEvent(currentUserId(jwt), request);
    }

    @PutMapping("/eventos/{eventId}")
    public CalendarEventResponse updateEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long eventId,
            @Valid @RequestBody CalendarEventRequest request) {
        return agendaService.updateEvent(currentUserId(jwt), eventId, request);
    }

    @DeleteMapping("/eventos/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        agendaService.deleteEvent(currentUserId(jwt), eventId);
    }

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
