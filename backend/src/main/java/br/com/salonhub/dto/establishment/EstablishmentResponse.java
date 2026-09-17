package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.Establishment;
import java.util.List;

public record EstablishmentResponse(
        Long id,
        String name,
        String cnpj,
        String email,
        String phone,
        boolean active,
        List<BusinessHourResponse> businessHours,
        Integer minimumAdvanceHours,
        Integer maximumAdvanceDays
) {
    public static EstablishmentResponse fromEntity(Establishment establishment, List<BusinessHourResponse> businessHours) {
        return new EstablishmentResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getCnpj(),
                establishment.getEmail(),
                establishment.getPhone(),
                establishment.isActive(),
                businessHours,
                establishment.getMinimumAdvanceMinutes() / 60,
                establishment.getMaximumAdvanceDays()
        );
    }
}
