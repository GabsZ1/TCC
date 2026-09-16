package br.com.salonhub.controller;

import br.com.salonhub.dto.catalog.CatalogProfessionalResponse;
import br.com.salonhub.dto.catalog.CatalogServiceResponse;
import br.com.salonhub.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/estabelecimentos/{establishmentId}/catalogo")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/servicos")
    public List<CatalogServiceResponse> findServices(@PathVariable Long establishmentId) {
        return catalogService.findServices(establishmentId);
    }

    @GetMapping("/funcionarios")
    public List<CatalogProfessionalResponse> findProfessionals(
            @PathVariable Long establishmentId,
            @RequestParam(required = false) Long serviceId) {
        return catalogService.findProfessionals(establishmentId, serviceId);
    }
}
