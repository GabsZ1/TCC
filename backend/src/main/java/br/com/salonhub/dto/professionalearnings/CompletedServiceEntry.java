package br.com.salonhub.dto.professionalearnings;

import br.com.salonhub.entity.Appointment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Um atendimento concluído pelo profissional, com os valores congelados no agendamento. */
public record CompletedServiceEntry(
        Long appointmentId,
        LocalDateTime completedAt,
        Long serviceId,
        String serviceName,
        String clientName,
        BigDecimal servicePrice,
        BigDecimal commissionPercentage,
        BigDecimal commissionAmount
) {
    public static CompletedServiceEntry fromEntity(Appointment appointment) {
        return new CompletedServiceEntry(
                appointment.getId(),
                appointment.getCompletedAt(),
                appointment.getService().getId(),
                appointment.getServiceName(),
                appointment.getClient().getName(),
                appointment.getAgreedPrice(),
                appointment.getCommissionPercentage(),
                appointment.getCommissionAmount());
    }
}
