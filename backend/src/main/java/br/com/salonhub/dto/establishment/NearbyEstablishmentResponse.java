package br.com.salonhub.dto.establishment;
public record NearbyEstablishmentResponse(Long id, String name, Integer minimumAdvanceHours, Integer maximumAdvanceDays, Double distanceKm) {}
