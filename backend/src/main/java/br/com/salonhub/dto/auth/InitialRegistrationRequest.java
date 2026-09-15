package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import br.com.salonhub.validation.ValidCnpj;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record InitialRegistrationRequest(
        @NotBlank @Size(max = 150) String ownerName,
        @NotBlank @Email @Size(max = 150) String ownerEmail,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String establishmentName,
        @NotBlank(message = "O CNPJ é obrigatório") @ValidCnpj String cnpj,
        @Email @Size(max = 150) String establishmentEmail,
        @Size(max = 20) String establishmentPhone,
        @Min(value = 0, message = "A antecedência mínima não pode ser negativa")
        @Max(value = 168, message = "A antecedência mínima deve ter no máximo 168 horas")
        Integer minimumAdvanceHours,
        @Min(value = 1, message = "A antecedência máxima deve ser de pelo menos 1 dia")
        @Max(value = 365, message = "A antecedência máxima deve ter no máximo 365 dias")
        Integer maximumAdvanceDays
) {
}
