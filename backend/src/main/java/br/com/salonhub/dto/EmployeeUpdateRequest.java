package br.com.salonhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmployeeUpdateRequest(
        @NotBlank(message = "O nome é obrigatório")
        @JsonProperty("nome") String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        String email
) {}
