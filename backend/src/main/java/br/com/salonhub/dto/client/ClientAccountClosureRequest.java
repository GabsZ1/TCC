package br.com.salonhub.dto.client;

import jakarta.validation.constraints.NotBlank;

/** US 2.26 — a exclusão exige a senha atual como confirmação explícita. */
public record ClientAccountClosureRequest(
        @NotBlank(message = "Informe sua senha para confirmar a exclusão")
        String password
) {
}
