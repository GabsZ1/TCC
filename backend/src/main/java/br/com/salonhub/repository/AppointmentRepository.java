package br.com.salonhub.repository;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    boolean existsByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            Long professionalId,
            AppointmentStatus status,
            LocalDateTime requestedEnd,
            LocalDateTime requestedStart
    );

    List<Appointment> findByClientIdOrderByStartsAtDesc(Long clientId);

    List<Appointment> findByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long professionalId,
            AppointmentStatus status,
            LocalDateTime periodEnd,
            LocalDateTime periodStart
    );
}
