package br.com.salonhub.dto.professionalearnings;

import java.time.LocalDate;
import java.util.List;

/**
 * Resposta da US 2.15: período efetivamente aplicado, filtro de serviço,
 * atendimentos (mais recentes primeiro), resumo e os tipos de serviço que o
 * profissional já realizou (para montar o filtro).
 */
public record ProfessionalCompletedServicesResponse(
        LocalDate startDate,
        LocalDate endDate,
        Long serviceId,
        List<CompletedServiceEntry> entries,
        CompletedServicesSummary summary,
        List<ServiceOption> availableServices,
        int page,
        int totalPages,
        long totalElements
) {
}
