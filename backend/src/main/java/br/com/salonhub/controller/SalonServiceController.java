package br.com.salonhub.controller;

import br.com.salonhub.dto.service.EstablishmentServiceRequest;
import br.com.salonhub.dto.service.EstablishmentServiceResponse;
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
    public ResponseEntity<EstablishmentServiceResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceCatalogService.create(currentUserId(jwt), request));
    }

    @GetMapping
    public ResponseEntity<List<EstablishmentServiceResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(serviceCatalogService.findAll(currentUserId(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EstablishmentServiceResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentServiceRequest request) {
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
