package br.com.salonhub.dto.catalog;

import br.com.salonhub.entity.EstablishmentService;

import java.math.BigDecimal;

/** Define os dados devolvidos pela API no fluxo de catálogo público, sem expor as entidades persistidas. */
public record CatalogServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes
) {
    public static CatalogServiceResponse fromEntity(EstablishmentService service) {
        return new CatalogServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getDurationMinutes()
        );
    }
}
