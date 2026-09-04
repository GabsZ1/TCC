package br.com.salonhub.dto;

import br.com.salonhub.entity.SalonService;
import java.math.BigDecimal;

public record SalonServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        BigDecimal commissionPercentage,
        boolean active
) {
    public static SalonServiceResponse fromEntity(SalonService service) {
        return new SalonServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getCommissionPercentage(),
                service.isActive()
        );
    }
}
