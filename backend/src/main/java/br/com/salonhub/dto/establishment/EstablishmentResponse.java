package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.Establishment;
import java.time.LocalTime;

public record EstablishmentResponse(
        Long id,
        String name,
        String cnpj,
        String email,
        String phone,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean active
) {
    public static EstablishmentResponse fromEntity(Establishment establishment) {
        return new EstablishmentResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getCnpj(),
                establishment.getEmail(),
                establishment.getPhone(),
                establishment.getOpeningTime(),
                establishment.getClosingTime(),
                establishment.isActive()
        );
    }
}
