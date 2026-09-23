package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.Establishment;

import java.time.OffsetDateTime;

public record SystemEstablishmentResponse(
        Long id,
        String name,
        String cnpj,
        String email,
        boolean active,
        boolean paused,
        boolean pausedBySystem,
        boolean bookingsBlocked,
        boolean bookingsBlockedBySystem,
        OffsetDateTime createdAt
) {
    public static SystemEstablishmentResponse fromEntity(Establishment establishment) {
        return new SystemEstablishmentResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getCnpj(),
                establishment.getEmail(),
                establishment.isActive(),
                establishment.isPaused(),
                establishment.isPausedBySystem(),
                establishment.isBookingsBlocked(),
                establishment.isBookingsBlockedBySystem(),
                establishment.getCreatedAt());
    }
}
