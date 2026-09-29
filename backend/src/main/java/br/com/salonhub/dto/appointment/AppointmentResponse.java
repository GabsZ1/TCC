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
        Long customerId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        AppointmentStatus status,
        String invalidationReason,
        LocalDateTime completedAt,
        String cancellationReason,
        LocalDateTime cancelledAt,
        Long cancelledByUserId,
        String cancelledByUserName
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
                appointment.getCustomer().getUser() == null ? null : appointment.getCustomer().getUser().getId(),
                appointment.getCustomer().getName(),
                appointment.getCustomer().getId(),
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getStatus(),
                appointment.getInvalidationReason(),
                appointment.getCompletedAt(),
                appointment.getCancellationReason(),
                appointment.getCancelledAt(),
                appointment.getCancelledBy() == null ? null : appointment.getCancelledBy().getId(),
                appointment.getCancelledBy() == null ? null : appointment.getCancelledBy().getName()
        );
    }
}
