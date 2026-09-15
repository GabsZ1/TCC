package br.com.salonhub.dto.establishment;

public record ActiveEstablishmentResponse(
        Long id,
        String name,
        Integer minimumAdvanceHours,
        Integer maximumAdvanceDays) {
}
