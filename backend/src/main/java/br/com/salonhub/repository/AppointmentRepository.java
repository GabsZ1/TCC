package br.com.salonhub.repository;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    List<Appointment> findByProfessionalIdAndStatusAndStartsAtGreaterThanOrderByStartsAt(
            Long professionalId,
            AppointmentStatus status,
            LocalDateTime after
    );

    List<Appointment> findByProfessionalIdAndStatusAndEndsAtGreaterThanOrderByStartsAt(
            Long professionalId,
            AppointmentStatus status,
            LocalDateTime after
    );

    List<Appointment> findByClientIdOrderByStartsAtDesc(Long clientId);

    Optional<Appointment> findByIdAndClientId(Long id, Long clientId);

    Optional<Appointment> findByIdAndEstablishmentId(Long id, Long establishmentId);

    List<Appointment> findByProfessionalIdOrderByStartsAtDesc(Long professionalId);

    List<Appointment> findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long professionalId, LocalDateTime periodEnd, LocalDateTime periodStart);

    @Query("""
            select appointment
            from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and (cast(:dayStart as LocalDateTime) is null or (
                    appointment.startsAt >= :dayStart
                    and appointment.startsAt < :dayEnd))
              and (:professionalId is null or appointment.professional.id = :professionalId)
              and (:status is null or appointment.status = :status)
            order by appointment.startsAt desc
            """)
    List<Appointment> findEstablishmentAgenda(
            @Param("establishmentId") Long establishmentId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("professionalId") Long professionalId,
            @Param("status") AppointmentStatus status);

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

    long countByEstablishmentIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long establishmentId,
            AppointmentStatus status,
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    );

    @Query("""
            select sum(appointment.commissionAmount)
            from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and appointment.status = :status
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
            """)
    BigDecimal sumCommissionByEstablishmentIdAndStatusAndCompletedAtBetween(
            @Param("establishmentId") Long establishmentId,
            @Param("status") AppointmentStatus status,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    @Query("""
            select
                professional.id as professionalId,
                professional.name as professionalName,
                count(appointment) as completedServicesCount,
                sum(appointment.agreedPrice) as totalServiceValue,
                sum(appointment.commissionAmount) as totalCommission
            from Appointment appointment
            join appointment.professional professional
            where appointment.establishment.id = :establishmentId
              and appointment.status = :status
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:professionalId is null or professional.id = :professionalId)
            group by professional.id, professional.name
            order by professional.name asc
            """)
    List<CommissionReportRow> findCommissionReportRows(
            @Param("establishmentId") Long establishmentId,
            @Param("status") AppointmentStatus status,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("professionalId") Long professionalId);
}
