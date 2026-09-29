package br.com.salonhub.dto.report;

import br.com.salonhub.entity.Appointment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Linha da US 2.13: um atendimento finalizado, com os valores congelados no agendamento. */
public record CompletedAppointmentEntry(
        Long appointmentId,
        LocalDateTime completedAt,
        Long professionalId,
        String professionalName,
        Long serviceId,
        String serviceName,
        String clientName,
        BigDecimal servicePrice,
        BigDecimal commissionPercentage,
        BigDecimal commissionAmount
) {
    public static CompletedAppointmentEntry fromEntity(Appointment appointment) {
        return new CompletedAppointmentEntry(
                appointment.getId(),
                appointment.getCompletedAt(),
                appointment.getProfessional().getId(),
                appointment.getProfessional().getName(),
                appointment.getService().getId(),
                appointment.getServiceName(),
                appointment.getCustomer().getName(),
                appointment.getAgreedPrice(),
                appointment.getCommissionPercentage(),
                appointment.getCommissionAmount());
    }
}
