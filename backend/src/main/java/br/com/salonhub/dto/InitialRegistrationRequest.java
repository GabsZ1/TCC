package br.com.salonhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitialRegistrationRequest(
        @NotBlank @Size(max = 150) String ownerName,
        @NotBlank @Email @Size(max = 150) String ownerEmail,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String establishmentName,
        @NotBlank @Pattern(regexp = "(?:\\D*\\d){14}\\D*", message = "deve conter 14 dígitos") String cnpj,
        @Email @Size(max = 150) String establishmentEmail,
        @Size(max = 20) String establishmentPhone
) {
}
