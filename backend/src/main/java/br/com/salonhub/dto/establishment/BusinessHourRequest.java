package br.com.salonhub.dto.establishment;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** Define os dados recebidos pela API no fluxo de estabelecimentos e suas validações de entrada. */
public record BusinessHourRequest(
        @NotNull DayOfWeek dayOfWeek,
        boolean closed,
        LocalTime openingTime,
        LocalTime closingTime
) {
}
