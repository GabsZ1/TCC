package br.com.salonhub.dto.employee;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ProfessionalServicesRequest(
        boolean allServices,
        @NotNull Set<Long> serviceIds
) {
}
