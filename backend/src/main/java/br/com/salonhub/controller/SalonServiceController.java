package br.com.salonhub.controller;

import br.com.salonhub.dto.SalonServiceRequest;
import br.com.salonhub.dto.SalonServiceResponse;
import br.com.salonhub.service.ServiceCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicos")
public class SalonServiceController {

    private final ServiceCatalogService serviceCatalogService;

    public SalonServiceController(ServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    @PostMapping
    public ResponseEntity<SalonServiceResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SalonServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceCatalogService.create(currentUserId(jwt), request));
    }

    @GetMapping
    public ResponseEntity<List<SalonServiceResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(serviceCatalogService.findAll(currentUserId(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalonServiceResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SalonServiceRequest request) {
        return ResponseEntity.ok(serviceCatalogService.update(currentUserId(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        serviceCatalogService.deactivate(currentUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
