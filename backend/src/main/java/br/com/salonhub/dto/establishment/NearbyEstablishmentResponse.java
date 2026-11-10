package br.com.salonhub.dto.establishment;
/** Define os dados devolvidos pela API no fluxo de estabelecimentos, sem expor as entidades persistidas. */
public record NearbyEstablishmentResponse(Long id, String name, Integer minimumAdvanceHours, Integer maximumAdvanceDays, Double distanceKm, boolean hasLogo) {}
