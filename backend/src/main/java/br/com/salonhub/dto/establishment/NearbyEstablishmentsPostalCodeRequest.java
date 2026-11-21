package br.com.salonhub.dto.establishment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Permite ordenar estabelecimentos a partir de um CEP informado pelo cliente. */
public record NearbyEstablishmentsPostalCodeRequest(
        @NotBlank(message = "Informe o CEP")
        @Pattern(regexp = "\\d{8}", message = "O CEP deve conter 8 números")
        String postalCode) {}
