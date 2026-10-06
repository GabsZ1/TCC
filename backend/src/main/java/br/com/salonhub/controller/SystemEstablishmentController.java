package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.establishment.SystemEstablishmentDetailsResponse;
import br.com.salonhub.dto.establishment.SystemEstablishmentResponse;
import br.com.salonhub.service.SystemEstablishmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@PreAuthorize(AccessRules.SYSTEM_ADMIN)
@RestController
@RequestMapping("/api/admin-sistema/estabelecimentos")
@RequiredArgsConstructor
public class SystemEstablishmentController {

    private final SystemEstablishmentService systemEstablishmentService;

    @GetMapping
    public List<SystemEstablishmentResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return systemEstablishmentService.findAll(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/{establishmentId}")
    public SystemEstablishmentDetailsResponse findDetails(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long establishmentId) {
        return systemEstablishmentService.findDetails(Long.valueOf(jwt.getSubject()), establishmentId);
    }

    @DeleteMapping("/{establishmentId}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long establishmentId) {
        systemEstablishmentService.deactivate(Long.valueOf(jwt.getSubject()), establishmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{establishmentId}/ativacao")
    public ResponseEntity<Void> reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long establishmentId) {
        systemEstablishmentService.reactivate(Long.valueOf(jwt.getSubject()), establishmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{establishmentId}/bloqueio-agendamentos")
    public ResponseEntity<Void> blockBookings(@AuthenticationPrincipal Jwt jwt, @PathVariable Long establishmentId) {
        systemEstablishmentService.blockBookings(Long.valueOf(jwt.getSubject()), establishmentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{establishmentId}/bloqueio-agendamentos")
    public ResponseEntity<Void> allowBookings(@AuthenticationPrincipal Jwt jwt, @PathVariable Long establishmentId) {
        systemEstablishmentService.allowBookings(Long.valueOf(jwt.getSubject()), establishmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{establishmentId}/pausa")
    public ResponseEntity<Void> pause(@AuthenticationPrincipal Jwt jwt, @PathVariable Long establishmentId) {
        systemEstablishmentService.pause(Long.valueOf(jwt.getSubject()), establishmentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{establishmentId}/pausa")
    public ResponseEntity<Void> resume(@AuthenticationPrincipal Jwt jwt, @PathVariable Long establishmentId) {
        systemEstablishmentService.resume(Long.valueOf(jwt.getSubject()), establishmentId);
        return ResponseEntity.noContent().build();
    }
}
