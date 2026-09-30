package br.com.salonhub.controller;

import br.com.salonhub.dto.professionalagenda.*;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TenantAuthorizationService;
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

@RestController
@RequestMapping("/api/funcionarios/{professionalId}/agenda")
@RequiredArgsConstructor
public class AdministrativeProfessionalAgendaController {
    private final ProfessionalAgendaService agenda;
    private final TenantAuthorizationService authorization;
    private final UserRepository users;
    private final Clock clock;

    private Long permittedProfessional(Jwt jwt, Long professionalId) {
        Long establishmentId = authorization.requireAdministrator(Long.valueOf(jwt.getSubject())).getEstablishment().getId();
        return users.findByIdAndEstablishmentIdAndRoleAndActiveTrue(professionalId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado."))
                .getId();
    }

    @GetMapping("/horarios")
    public List<ProfessionalHourResponse> schedule(@AuthenticationPrincipal Jwt jwt, @PathVariable Long professionalId) {
        return agenda.findSchedule(permittedProfessional(jwt, professionalId));
    }

    @GetMapping("/configuracao")
    public ProfessionalAgendaConfigurationResponse configuration(@AuthenticationPrincipal Jwt jwt, @PathVariable Long professionalId) {
        permittedProfessional(jwt, professionalId);
        return new ProfessionalAgendaConfigurationResponse(clock.getZone().getId());
    }

    @PutMapping("/horarios")
    public List<ProfessionalHourResponse> updateSchedule(@AuthenticationPrincipal Jwt jwt, @PathVariable Long professionalId,
            @Valid @RequestBody ProfessionalScheduleUpdateRequest request) {
        return agenda.updateSchedule(permittedProfessional(jwt, professionalId), request);
    }

    @GetMapping
    public List<ProfessionalAgendaItemResponse> items(@AuthenticationPrincipal Jwt jwt, @PathVariable Long professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt) {
        return agenda.findAgenda(permittedProfessional(jwt, professionalId), startsAt, endsAt);
    }

    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse createEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long professionalId,
            @Valid @RequestBody CalendarEventRequest request) {
        return agenda.createEvent(permittedProfessional(jwt, professionalId), request);
    }

    @PutMapping("/eventos/{eventId}")
    public CalendarEventResponse updateEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long professionalId,
            @PathVariable Long eventId, @Valid @RequestBody CalendarEventRequest request) {
        return agenda.updateEvent(permittedProfessional(jwt, professionalId), eventId, request);
    }

    @DeleteMapping("/eventos/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long professionalId, @PathVariable Long eventId) {
        agenda.deleteEvent(permittedProfessional(jwt, professionalId), eventId);
    }
}
