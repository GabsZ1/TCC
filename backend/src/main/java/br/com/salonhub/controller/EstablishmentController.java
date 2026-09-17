package br.com.salonhub.controller;

import br.com.salonhub.dto.dashboard.DashboardSummaryResponse;
import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.dto.establishment.EstablishmentUpdateRequest;
import br.com.salonhub.service.DashboardService;
import br.com.salonhub.service.EstablishmentManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/estabelecimentos/me")
@RequiredArgsConstructor
public class EstablishmentController {

    private final EstablishmentManagementService establishmentManagementService;
    private final DashboardService dashboardService;

    @GetMapping
    public EstablishmentResponse findMine(@AuthenticationPrincipal Jwt jwt) {
        return establishmentManagementService.findMine(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/dashboard")
    public DashboardSummaryResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.getSummary(Long.valueOf(jwt.getSubject()));
    }

    @PutMapping
    public EstablishmentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentUpdateRequest request) {
        return establishmentManagementService.update(Long.valueOf(jwt.getSubject()), request);
    }

    @PatchMapping("/ativacao")
    public EstablishmentResponse reactivate(@AuthenticationPrincipal Jwt jwt) {
        return establishmentManagementService.reactivate(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal Jwt jwt) {
        establishmentManagementService.deactivate(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
