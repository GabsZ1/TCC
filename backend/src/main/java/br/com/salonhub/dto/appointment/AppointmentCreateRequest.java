package br.com.salonhub.dto.appointment;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AppointmentCreateRequest(
        @NotNull(message = "O serviço é obrigatório")
        Long serviceId,

        @NotNull(message = "O funcionário é obrigatório")
        Long professionalId,

        @NotNull(message = "A data e o horário são obrigatórios")
        @Future(message = "O agendamento deve estar no futuro")
        LocalDateTime startsAt
) {
}
