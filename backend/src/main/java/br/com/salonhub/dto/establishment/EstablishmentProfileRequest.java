package br.com.salonhub.dto.establishment;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EstablishmentProfileRequest(
        @Size(max = 3000, message = "O Quem Somos deve ter no máximo 3000 caracteres")
        String aboutUs,

        @Size(max = 100, message = "O Instagram deve ter no máximo 100 caracteres")
        String instagram,

        @Size(max = 20, message = "O WhatsApp deve ter no máximo 20 caracteres")
        String whatsapp,

        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String phone,

        @Pattern(regexp = "^$|\\d{8}$", message = "O CEP deve conter 8 números")
        String postalCode,

        @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres")
        String street,

        @Size(max = 20, message = "O número deve ter no máximo 20 caracteres")
        String streetNumber,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres")
        String addressComplement,

        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
        String neighborhood,

        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres")
        String city,

        @Pattern(regexp = "^$|(?i)AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO$",
                message = "Informe uma UF válida")
        String state
) {
}

