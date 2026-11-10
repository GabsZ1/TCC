package br.com.salonhub.dto.employee;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Define os dados recebidos pela API no fluxo de funcionários e suas validações de entrada. */
public record ProfessionalServicesRequest(
        boolean allServices,
        @NotNull Set<Long> serviceIds
) {
}
