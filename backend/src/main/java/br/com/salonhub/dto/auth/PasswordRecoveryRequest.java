package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordRecoveryRequest(
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "Informe um e-mail válido")
        @Size(max = 150)
        String email
) {
}
