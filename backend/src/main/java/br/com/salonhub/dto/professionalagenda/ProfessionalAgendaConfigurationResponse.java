package br.com.salonhub.dto.professionalagenda;

/** Define os dados devolvidos pela API no fluxo de agenda do profissional, sem expor as entidades persistidas. */
public record ProfessionalAgendaConfigurationResponse(String timeZone) {
}
