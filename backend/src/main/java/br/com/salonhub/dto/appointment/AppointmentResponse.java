package br.com.salonhub.dto.appointment;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        Long establishmentId,
        String establishmentName,
        Long serviceId,
        String serviceName,
        Long professionalId,
        String professionalName,
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
                appointment.getService().getName(),
                appointment.getProfessional().getId(),
                appointment.getProfessional().getName(),
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getStatus()
        );
    }
}
