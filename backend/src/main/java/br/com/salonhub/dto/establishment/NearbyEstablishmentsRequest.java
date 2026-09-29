package br.com.salonhub.dto.establishment;
import jakarta.validation.constraints.NotNull;
public record NearbyEstablishmentsRequest(@NotNull(message = "Informe a latitude") Double latitude,
        @NotNull(message = "Informe a longitude") Double longitude) {}
