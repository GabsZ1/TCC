package br.com.salonhub.dto.establishment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record EstablishmentUpdateRequest(
        @NotBlank(message = "O nome do estabelecimento é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @Email(message = "E-mail em formato inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String phone,

        @Size(min = 7, max = 7, message = "Informe os horários dos sete dias da semana")
        List<@Valid BusinessHourRequest> businessHours,

        @Min(value = 0, message = "A antecedência mínima não pode ser negativa")
        @Max(value = 168, message = "A antecedência mínima deve ter no máximo 168 horas")
        Integer minimumAdvanceHours,

        @Min(value = 1, message = "A antecedência máxima deve ser de pelo menos 1 dia")
        @Max(value = 365, message = "A antecedência máxima deve ter no máximo 365 dias")
        Integer maximumAdvanceDays
) {
}
