package br.com.salonhub.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US 2.24 — edição do próprio perfil. A troca de senha é feita em PATCH /api/auth/senha. */
public record ClientProfileUpdateRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Informe um e-mail válido!")
        @Size(max = 150)
        String email,

        @NotBlank(message = "O telefone é obrigatório")
        @Size(max = 20, message = "Informe um telefone válido!")
        String phone
) {
}
