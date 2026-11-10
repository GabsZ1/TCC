package br.com.salonhub.dto.employee;

import br.com.salonhub.dto.service.EstablishmentServiceResponse;
import br.com.salonhub.entity.User;

import java.util.List;

/** Define os dados devolvidos pela API no fluxo de funcionários, sem expor as entidades persistidas. */
public record ProfessionalServicesResponse(
        Long professionalId,
        boolean allServices,
        List<EstablishmentServiceResponse> services
) {
    public static ProfessionalServicesResponse fromEntity(User professional) {
        return new ProfessionalServicesResponse(
                professional.getId(),
                professional.isOffersAllServices(),
                professional.getOfferedServices().stream()
                        .filter(service -> service.isActive())
                        .map(EstablishmentServiceResponse::fromEntity)
                        .toList());
    }
}
