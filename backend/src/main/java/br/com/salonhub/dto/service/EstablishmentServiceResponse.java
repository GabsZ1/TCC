package br.com.salonhub.dto.service;

import br.com.salonhub.entity.EstablishmentService;
import java.math.BigDecimal;

public record EstablishmentServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        BigDecimal commissionPercentage,
        Integer durationMinutes,
        boolean active
) {
    public static EstablishmentServiceResponse fromEntity(EstablishmentService service) {
        return new EstablishmentServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getCommissionPercentage(),
                service.getDurationMinutes(),
                service.isActive()
        );
    }
}
