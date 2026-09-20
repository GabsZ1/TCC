package br.com.salonhub.dto.professionalagenda;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfessionalScheduleUpdateRequest(
        @NotEmpty @Size(min = 7, max = 7) List<@Valid ProfessionalHourRequest> businessHours) {
}
