package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentRescheduleRequest;
import br.com.salonhub.dto.appointment.AppointmentCancellationRequest;
import br.com.salonhub.dto.appointment.AppointmentInvalidationRequest;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.dto.PagedResponse;
import br.com.salonhub.dto.appointment.AvailabilityResponse;
import br.com.salonhub.dto.appointment.AdministrativeProfessionalAvailabilityResponse;
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

    @PreAuthorize(AccessRules.CLIENT)
    @PostMapping("/estabelecimentos/{establishmentId}/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long establishmentId,
            @Valid @RequestBody AppointmentCreateRequest request) {
        return commandService.create(currentUserId(jwt), establishmentId, request);
    }

    @PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
    @PostMapping("/agendamentos/estabelecimento/me/clientes/{customerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createForCustomer(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long customerId, @Valid @RequestBody AppointmentCreateRequest request) {
        return commandService.createForCustomer(currentUserId(jwt), customerId, request);
    }

    @PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
    @GetMapping("/agendamentos/estabelecimento/me/clientes/{customerId}/disponibilidade")
    public AvailabilityResponse findInternalAvailability(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long customerId, @RequestParam Long serviceId, @RequestParam Long professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long appointmentId) {
        return queryService.findInternalAvailability(currentUserId(jwt), customerId, serviceId, professionalId, date, appointmentId);
    }

    @PreAuthorize(AccessRules.CLIENT)
    @GetMapping("/agendamentos/me")
    public PagedResponse<AppointmentResponse> findMine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(queryService.findMine(currentUserId(jwt),
                Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @PreAuthorize(AccessRules.CLIENT)
    @GetMapping("/agendamentos/me/proximo")
    public AppointmentResponse findNextForClient(@AuthenticationPrincipal Jwt jwt) {
        return queryService.findNextForClient(currentUserId(jwt));
    }

    @PreAuthorize(AccessRules.CLIENT)
    @GetMapping("/agendamentos/me/pagina")
    public PagedResponse<AppointmentResponse> findMinePage(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean history, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return PagedResponse.from(queryService.findMinePage(currentUserId(jwt), history,
                Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @PreAuthorize(AccessRules.PROFESSIONAL)
    @GetMapping("/agendamentos/profissional/me/hoje")
    public AppointmentQueryService.ProfessionalToday findTodayForProfessional(@AuthenticationPrincipal Jwt jwt) {
        return queryService.findTodayForProfessional(currentUserId(jwt));
    }

    @PreAuthorize(AccessRules.CLIENT)
    @PutMapping("/agendamentos/{appointmentId}")
    public AppointmentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentCreateRequest request) {
        return commandService.update(currentUserId(jwt), appointmentId, request);
    }

    @PreAuthorize(AccessRules.CLIENT)
    @PatchMapping("/agendamentos/{appointmentId}/cancelamento")
    public AppointmentResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId) {
        return commandService.cancel(currentUserId(jwt), appointmentId);
    }

    @PreAuthorize(AccessRules.ADMIN)
    @PatchMapping("/agendamentos/{appointmentId}/cancelamento-administrativo")
    public AppointmentResponse cancelAsAdministrator(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentCancellationRequest request) {
        return commandService.cancelAsAdministrator(currentUserId(jwt), appointmentId, request.reason());
    }
    /** Administrador remarca e/ou troca o profissional de um agendamento confirmado. */
    @PreAuthorize(AccessRules.ADMIN)
    @PutMapping("/agendamentos/{appointmentId}/remarcacao")
    public AppointmentResponse rescheduleAsAdministrator(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentRescheduleRequest request) {
        return commandService.rescheduleAsAdministrator(currentUserId(jwt), appointmentId, request);
    }

    @PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
    @PatchMapping("/agendamentos/{appointmentId}/conclusao")
    public AppointmentResponse complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId) {
        return commandService.complete(currentUserId(jwt), appointmentId);
    }

    @PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
    @PatchMapping("/agendamentos/{appointmentId}/falta")
    public AppointmentResponse markNoShow(@AuthenticationPrincipal Jwt jwt, @PathVariable Long appointmentId) {
        return commandService.markNoShow(currentUserId(jwt), appointmentId);
    }

    @PreAuthorize(AccessRules.ADMIN)
    @PatchMapping("/agendamentos/{appointmentId}/invalidacao")
    public AppointmentResponse invalidate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentInvalidationRequest request) {
        return commandService.invalidate(currentUserId(jwt), appointmentId, request.reason());
    }

    @PreAuthorize(AccessRules.PROFESSIONAL)
    @GetMapping("/agendamentos/profissional/me")
    public PagedResponse<AppointmentResponse> findMineAsProfessional(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(queryService.findMineAsProfessional(currentUserId(jwt),
                Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    /** Atendimentos que já terminaram e ainda não foram concluídos nem marcados como falta. */
    @PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
    @GetMapping("/agendamentos/pendentes-fechamento")
    public PagedResponse<AppointmentResponse> findPendingClosure(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return PagedResponse.from(queryService.findPendingClosure(currentUserId(jwt),
                Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @PreAuthorize(AccessRules.ADMIN)
    @GetMapping("/agendamentos/estabelecimento/me")
    public PagedResponse<AppointmentResponse> findEstablishmentAgenda(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long professionalId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(queryService.findEstablishmentAgenda(
                currentUserId(jwt), date, professionalId, status,
                Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @PreAuthorize(AccessRules.ADMIN)
    @GetMapping("/agendamentos/estabelecimento/me/disponibilidade-equipe")
    public List<AdministrativeProfessionalAvailabilityResponse> findTeamAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long professionalId) {
        return queryService.findTeamAvailability(currentUserId(jwt), date, professionalId);
    }

    @PreAuthorize(AccessRules.CLIENT)
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
