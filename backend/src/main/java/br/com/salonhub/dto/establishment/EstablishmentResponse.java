package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.Establishment;
import java.time.LocalTime;
import java.util.List;

public record EstablishmentResponse(
        Long id,
        String name,
        String cnpj,
        String email,
        String phone,
        LocalTime openingTime,
        LocalTime closingTime,
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
                establishment.getOpeningTime(),
                establishment.getClosingTime(),
                establishment.isActive(),
                businessHours,
                establishment.getMinimumAdvanceMinutes() / 60,
                establishment.getMaximumAdvanceDays()
        );
    }
}
