package br.com.salonhub.dto.establishment;
import jakarta.validation.constraints.NotNull;
/** Define os dados recebidos pela API no fluxo de estabelecimentos e suas validações de entrada. */
public record NearbyEstablishmentsRequest(@NotNull(message = "Informe a latitude") Double latitude,
        @NotNull(message = "Informe a longitude") Double longitude) {}
