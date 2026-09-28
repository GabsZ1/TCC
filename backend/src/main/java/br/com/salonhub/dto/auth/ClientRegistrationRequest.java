package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US 2.23 — Cadastrar cliente: nome, e-mail, telefone, senha e confirmação. */
public record ClientRegistrationRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150)
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Informe um e-mail válido!")
        @Size(max = 150)
        String email,

        @NotBlank(message = "O telefone é obrigatório")
        @Size(max = 20, message = "Informe um telefone válido!")
        String phone,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password,

        @NotBlank(message = "Confirme a senha")
        String passwordConfirmation
) {
}
