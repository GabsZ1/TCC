package br.com.salonhub.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record EstablishmentServiceRequest(
        @NotBlank(message = "O nome do serviço é obrigatório")
        String name,

        String description,

        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = false, message = "O preço deve ser maior que zero")
        BigDecimal price,

        @NotNull(message = "A comissão é obrigatória")
        @DecimalMin(value = "0.0", message = "A comissão não pode ser negativa")
        @DecimalMax(value = "100.0", message = "A comissão não pode exceder 100%")
        BigDecimal commissionPercentage
) {}
