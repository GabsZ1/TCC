package br.com.salonhub.controller;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentInvalidationRequest;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.dto.appointment.AvailabilityResponse;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.service.appointment.AppointmentCommandService;
import br.com.salonhub.service.appointment.AppointmentQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentCommandService commandService;
    private final AppointmentQueryService queryService;

    @PostMapping("/estabelecimentos/{establishmentId}/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long establishmentId,
            @Valid @RequestBody AppointmentCreateRequest request) {
        return commandService.create(currentUserId(jwt), establishmentId, request);
    }

    @GetMapping("/agendamentos/me")
    public List<AppointmentResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return queryService.findMine(currentUserId(jwt));
    }

    @PutMapping("/agendamentos/{appointmentId}")
    public AppointmentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentCreateRequest request) {
        return commandService.update(currentUserId(jwt), appointmentId, request);
    }

    @PatchMapping("/agendamentos/{appointmentId}/cancelamento")
    public AppointmentResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId) {
        return commandService.cancel(currentUserId(jwt), appointmentId);
    }

    @PatchMapping("/agendamentos/{appointmentId}/conclusao")
    public AppointmentResponse complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId) {
        return commandService.complete(currentUserId(jwt), appointmentId);
    }

    @PatchMapping("/agendamentos/{appointmentId}/invalidacao")
    public AppointmentResponse invalidate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentInvalidationRequest request) {
        return commandService.invalidate(currentUserId(jwt), appointmentId, request.reason());
    }

    @GetMapping("/agendamentos/profissional/me")
    public List<AppointmentResponse> findMineAsProfessional(@AuthenticationPrincipal Jwt jwt) {
        return queryService.findMineAsProfessional(currentUserId(jwt));
    }

    @GetMapping("/agendamentos/estabelecimento/me")
    public List<AppointmentResponse> findEstablishmentAgenda(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long professionalId,
            @RequestParam(required = false) AppointmentStatus status) {
        return queryService.findEstablishmentAgenda(
                currentUserId(jwt), date, professionalId, status);
    }

    @GetMapping("/estabelecimentos/{establishmentId}/agendamentos/disponibilidade")
    public AvailabilityResponse findAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long establishmentId,
            @RequestParam Long serviceId,
            @RequestParam Long professionalId,
            @RequestParam(required = false) Long appointmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.findAvailability(
                currentUserId(jwt), establishmentId, serviceId, professionalId, date, appointmentId);
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
