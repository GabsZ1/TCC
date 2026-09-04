package br.com.salonhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitialRegistrationRequest(
        @JsonProperty("nomeResponsavel") @NotBlank @Size(max = 150) String ownerName,
        @JsonProperty("emailResponsavel") @NotBlank @Email @Size(max = 150) String ownerEmail,
        @JsonProperty("senha") @NotBlank @Size(min = 8, max = 72) String password,
        @JsonProperty("nomeEstabelecimento") @NotBlank @Size(max = 150) String establishmentName,
        @NotBlank @Pattern(regexp = "(?:\\D*\\d){14}\\D*", message = "deve conter 14 dígitos") String cnpj,
        @JsonProperty("emailEstabelecimento") @Email @Size(max = 150) String establishmentEmail,
        @JsonProperty("telefoneEstabelecimento") @Size(max = 20) String establishmentPhone
) {
}
