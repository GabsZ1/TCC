package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRegistrationRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150)
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        @Size(max = 150)
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password
) {
}
