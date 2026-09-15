package br.com.salonhub.repository;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    boolean existsByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            Long professionalId,
            AppointmentStatus status,
            LocalDateTime requestedEnd,
            LocalDateTime requestedStart
    );

    boolean existsByClientIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            Long clientId,
            AppointmentStatus status,
            LocalDateTime requestedEnd,
            LocalDateTime requestedStart
    );

    boolean existsByProfessionalIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
            Long professionalId,
            AppointmentStatus status,
            Long appointmentId,
            LocalDateTime requestedEnd,
            LocalDateTime requestedStart
    );

    boolean existsByClientIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
            Long clientId,
            AppointmentStatus status,
            Long appointmentId,
            LocalDateTime requestedEnd,
            LocalDateTime requestedStart
    );

    boolean existsByProfessionalId(Long professionalId);

    List<Appointment> findByClientIdOrderByStartsAtDesc(Long clientId);

    Optional<Appointment> findByIdAndClientId(Long id, Long clientId);

    List<Appointment> findByProfessionalIdOrderByStartsAtDesc(Long professionalId);

    List<Appointment> findByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long professionalId,
            AppointmentStatus status,
            LocalDateTime periodEnd,
            LocalDateTime periodStart
    );

    List<Appointment> findByClientIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long clientId,
            AppointmentStatus status,
            LocalDateTime periodEnd,
            LocalDateTime periodStart
    );
}
