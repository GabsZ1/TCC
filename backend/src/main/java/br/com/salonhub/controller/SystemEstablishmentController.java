package br.com.salonhub.controller;

import br.com.salonhub.dto.establishment.SystemEstablishmentResponse;
import br.com.salonhub.service.SystemEstablishmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin-sistema/estabelecimentos")
@RequiredArgsConstructor
public class SystemEstablishmentController {

    private final SystemEstablishmentService systemEstablishmentService;

    @GetMapping
    public List<SystemEstablishmentResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return systemEstablishmentService.findAll(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/{establishmentId}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long establishmentId) {
        systemEstablishmentService.deactivate(Long.valueOf(jwt.getSubject()), establishmentId);
        return ResponseEntity.noContent().build();
    }
}
