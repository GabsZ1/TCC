package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.Establishment;

import java.time.OffsetDateTime;

public record SystemEstablishmentResponse(
        Long id,
        String name,
        String cnpj,
        String email,
        String phone,
        boolean active,
        OffsetDateTime createdAt
) {
    public static SystemEstablishmentResponse fromEntity(Establishment establishment) {
        return new SystemEstablishmentResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getCnpj(),
                establishment.getEmail(),
                establishment.getPhone(),
                establishment.isActive(),
                establishment.getCreatedAt());
    }
}
