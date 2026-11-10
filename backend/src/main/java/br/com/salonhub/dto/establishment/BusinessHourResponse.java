package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.EstablishmentBusinessHour;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** Define os dados devolvidos pela API no fluxo de estabelecimentos, sem expor as entidades persistidas. */
public record BusinessHourResponse(DayOfWeek dayOfWeek, boolean closed, LocalTime openingTime, LocalTime closingTime) {
    public static BusinessHourResponse fromEntity(EstablishmentBusinessHour hour) {
        return new BusinessHourResponse(hour.getDayOfWeek(), hour.isClosed(), hour.getOpeningTime(), hour.getClosingTime());
    }
}
