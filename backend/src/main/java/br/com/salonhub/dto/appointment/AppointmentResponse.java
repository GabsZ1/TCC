package br.com.salonhub.dto.appointment;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record AppointmentResponse(
        Long id,
        Long establishmentId,
        String establishmentName,
        Long serviceId,
        String serviceName,
        BigDecimal agreedPrice,
        BigDecimal commissionPercentage,
        BigDecimal commissionAmount,
        Integer durationMinutes,
        Long professionalId,
        String professionalName,
        Long clientId,
        String clientName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        AppointmentStatus status
) {
    public static AppointmentResponse fromEntity(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getEstablishment().getId(),
                appointment.getEstablishment().getName(),
                appointment.getService().getId(),
                appointment.getServiceName(),
                appointment.getAgreedPrice(),
                appointment.getCommissionPercentage(),
                appointment.getCommissionAmount(),
                appointment.getDurationMinutes(),
                appointment.getProfessional().getId(),
                appointment.getProfessional().getName(),
                appointment.getClient().getId(),
                appointment.getClient().getName(),
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getStatus()
        );
    }
}
