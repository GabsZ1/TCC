package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.Establishment;
import java.util.List;

/** Define os dados devolvidos pela API no fluxo de estabelecimentos, sem expor as entidades persistidas. */
public record EstablishmentResponse(
        Long id,
        String name,
        String cnpj,
        String email,
        boolean active,
        boolean paused,
        boolean pausedBySystem,
        boolean bookingsBlocked,
        boolean bookingsBlockedBySystem,
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
                establishment.isActive(),
                establishment.isPaused(),
                establishment.isPausedBySystem(),
                establishment.isBookingsBlocked(),
                establishment.isBookingsBlockedBySystem(),
                businessHours,
                establishment.getMinimumAdvanceMinutes() / 60,
                establishment.getMaximumAdvanceDays()
        );
    }
}
