package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.catalog.CatalogProfessionalResponse;
import br.com.salonhub.dto.catalog.CatalogServiceResponse;
import br.com.salonhub.service.catalog.CatalogService;
import br.com.salonhub.service.employee.EmployeePhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** Expõe as operações HTTP de catálogo público e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.AUTHENTICATED)
@RestController
@RequestMapping("/api/estabelecimentos/{establishmentId}/catalogo")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final EmployeePhotoService employeePhotoService;

    /** Expõe o catálogo ativo de um estabelecimento ao cliente. */
    @GetMapping("/servicos")
    public List<CatalogServiceResponse> findServices(@PathVariable Long establishmentId) {
        return catalogService.findServices(establishmentId);
    }

    /** Lista profissionais disponíveis para o serviço escolhido. */
    @GetMapping("/funcionarios")
    public List<CatalogProfessionalResponse> findProfessionals(
            @PathVariable Long establishmentId,
            @RequestParam(required = false) Long serviceId) {
        return catalogService.findProfessionals(establishmentId, serviceId);
    }

    /** Foto de um profissional ativo, exibida ao cliente na escolha do profissional. */
    @GetMapping("/funcionarios/{professionalId}/foto")
    public ResponseEntity<byte[]> findProfessionalPhoto(
            @PathVariable Long establishmentId,
            @PathVariable Long professionalId) {
        EmployeePhotoService.StoredPhoto photo = employeePhotoService.findForCatalog(establishmentId, professionalId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(photo.data());
    }
}
