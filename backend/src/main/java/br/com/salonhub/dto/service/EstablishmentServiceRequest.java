package br.com.salonhub.dto.service;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record EstablishmentServiceRequest(
        @NotBlank(message = "O nome do serviço é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String description,

        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = false, message = "O preço deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "O preço deve ter no máximo 8 inteiros e 2 decimais")
        BigDecimal price,

        @DecimalMin(value = "0.0", message = "A comissão não pode ser negativa")
        @DecimalMax(value = "100.0", message = "A comissão não pode exceder 100%")
        BigDecimal commissionPercentage,

        @NotNull(message = "A duração é obrigatória")
        @Min(value = 1, message = "A duração deve ser maior que zero")
        @Max(value = 1440, message = "A duração deve ter no máximo 1440 minutos")
        Integer durationMinutes
) {}
