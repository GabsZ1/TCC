package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.service.EstablishmentServiceRequest;
import br.com.salonhub.dto.service.EstablishmentServiceResponse;
import br.com.salonhub.dto.service.ServiceCountsResponse;
import br.com.salonhub.dto.PagedResponse;
import br.com.salonhub.service.catalog.ServiceCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Expõe as operações HTTP de catálogo de serviços e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.ADMIN)
@RestController
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
public class SalonServiceController {

    private final ServiceCatalogService serviceCatalogService;

    /** Adiciona um serviço ao catálogo da unidade administrada. */
    @PostMapping
    public ResponseEntity<EstablishmentServiceResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceCatalogService.create(currentUserId(jwt), request));
    }

    /** Consulta serviços da unidade com filtros e paginação. */
    @GetMapping
    public ResponseEntity<PagedResponse<EstablishmentServiceResponse>> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(PagedResponse.from(serviceCatalogService.findAll(
                currentUserId(jwt), Math.max(0, page), Math.max(1, Math.min(size, 100)), active, search)));
    }

    /** Expõe totais de serviços ativos e inativos. */
    @GetMapping("/resumo")
    public ResponseEntity<ServiceCountsResponse> counts(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(serviceCatalogService.counts(currentUserId(jwt)));
    }

    /** Altera preço, duração e demais dados de um serviço da unidade. */
    @PutMapping("/{id}")
    public ResponseEntity<EstablishmentServiceResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentServiceRequest request) {
        return ResponseEntity.ok(serviceCatalogService.update(currentUserId(jwt), id, request));
    }

    /** Retira um serviço do catálogo sem apagar agendamentos antigos. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        serviceCatalogService.deactivate(currentUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Devolve um serviço desativado ao catálogo. */
    @PatchMapping("/{id}/ativacao")
    public ResponseEntity<EstablishmentServiceResponse> reactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(serviceCatalogService.reactivate(currentUserId(jwt), id));
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
