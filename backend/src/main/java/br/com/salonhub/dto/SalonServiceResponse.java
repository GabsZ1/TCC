package br.com.salonhub.dto;

import br.com.salonhub.entity.SalonService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record SalonServiceResponse(
        Long id,
        @JsonProperty("nome") String name,
        @JsonProperty("descricao") String description,
        @JsonProperty("preco") BigDecimal price,
        @JsonProperty("comissaoPorcentagem") BigDecimal commissionPercentage,
        @JsonProperty("ativo") boolean active
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
