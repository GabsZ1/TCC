package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import br.com.salonhub.validation.ValidCnpj;

/** Define os dados recebidos pela API no fluxo de autenticação e suas validações de entrada. */
public record InitialRegistrationRequest(
        @NotBlank @Size(max = 150) String ownerName,
        @NotBlank @Email @Size(max = 150) String ownerEmail,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String establishmentName,
        @NotBlank(message = "O CNPJ é obrigatório") @ValidCnpj String cnpj,
        @Email @Size(max = 150) String establishmentEmail,
        @Size(max = 20) String establishmentPhone
) {
}
