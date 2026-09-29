package br.com.salonhub.repository;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select appointment.establishment.id
            from Appointment appointment
            where appointment.id = :id
              and appointment.client.id = :clientId
            """)
    Optional<Long> findEstablishmentIdByIdAndClientId(
            @Param("id") Long id,
            @Param("clientId") Long clientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from Appointment appointment where appointment.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.id = :id
              and appointment.client.id = :clientId
            """)
    Optional<Appointment> findByIdAndClientIdForUpdate(
            @Param("id") Long id,
            @Param("clientId") Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.id = :id
              and appointment.establishment.id = :establishmentId
            """)
    Optional<Appointment> findByIdAndEstablishmentIdForUpdate(
            @Param("id") Long id,
            @Param("establishmentId") Long establishmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and appointment.status = :status
            order by appointment.id
            """)
    List<Appointment> findByEstablishmentIdAndStatusForUpdate(
            @Param("establishmentId") Long establishmentId,
            @Param("status") AppointmentStatus status);

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

    boolean existsByEstablishmentIdAndStatusAndEndsAtGreaterThan(
            Long establishmentId, AppointmentStatus status, LocalDateTime after);

    boolean existsByEstablishmentIdAndStatus(Long establishmentId, AppointmentStatus status);

    List<Appointment> findByEstablishmentIdAndStatus(Long establishmentId, AppointmentStatus status);

    List<Appointment> findByEstablishmentIdAndStatusAndEndsAtGreaterThanOrderByStartsAt(
            Long establishmentId, AppointmentStatus status, LocalDateTime after);

    boolean existsByProfessionalId(Long professionalId);

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

    long countByEstablishmentId(Long establishmentId);

    long countByEstablishmentIdAndStatusAndStartsAtGreaterThanEqual(
            Long establishmentId, AppointmentStatus status, LocalDateTime from);

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

    @Query("""
            select
                professional.id as professionalId,
                professional.name as professionalName,
                sum(case when appointment.status = br.com.salonhub.entity.AppointmentStatus.CONFIRMED then 1L else 0L end) as confirmedCount,
                sum(case when appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED then 1L else 0L end) as completedCount,
                sum(case when appointment.status = br.com.salonhub.entity.AppointmentStatus.CANCELLED then 1L else 0L end) as cancelledCount,
                sum(case when appointment.status = br.com.salonhub.entity.AppointmentStatus.INVALIDATED then 1L else 0L end) as invalidatedCount
            from Appointment appointment
            join appointment.professional professional
            where appointment.establishment.id = :establishmentId
              and appointment.startsAt >= :periodStart
              and appointment.startsAt < :periodEnd
              and (:professionalId is null or professional.id = :professionalId)
            group by professional.id, professional.name
            order by professional.name asc
            """)
    List<AppointmentsReportRow> findAppointmentsReportRows(
            @Param("establishmentId") Long establishmentId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("professionalId") Long professionalId);

    /** US 2.15 — atendimentos concluídos do próprio profissional, filtrados pela data de conclusão. */
    @Query("""
            select appointment
            from Appointment appointment
            join fetch appointment.client
            join fetch appointment.service
            where appointment.professional.id = :professionalId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:serviceId is null or appointment.service.id = :serviceId)
            order by appointment.completedAt desc, appointment.id desc
            """)
    List<Appointment> findCompletedByProfessional(
            @Param("professionalId") Long professionalId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("serviceId") Long serviceId);

    /** Tipos de serviço que o profissional já concluiu alguma vez (opções do filtro da US 2.15). */
    @Query("""
            select distinct new br.com.salonhub.dto.professionalearnings.ServiceOption(service.id, service.name)
            from Appointment appointment
            join appointment.service service
            where appointment.professional.id = :professionalId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
            order by service.name
            """)
    List<br.com.salonhub.dto.professionalearnings.ServiceOption> findCompletedServiceOptionsByProfessional(
            @Param("professionalId") Long professionalId);

    /** US 2.13 — atendimentos finalizados do estabelecimento, paginados, pela data de conclusão. */
    @Query(value = """
            select appointment
            from Appointment appointment
            join fetch appointment.professional
            join fetch appointment.client
            join fetch appointment.service
            where appointment.establishment.id = :establishmentId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:professionalId is null or appointment.professional.id = :professionalId)
            order by appointment.completedAt desc, appointment.id desc
            """,
            countQuery = """
            select count(appointment)
            from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:professionalId is null or appointment.professional.id = :professionalId)
            """)
    Page<Appointment> findCompletedByEstablishment(
            @Param("establishmentId") Long establishmentId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("professionalId") Long professionalId,
            Pageable pageable);

    /** Totais da US 2.13 sobre todos os atendimentos que atendem aos filtros (independe da página). */
    @Query("""
            select
                count(appointment) as totalAppointments,
                sum(appointment.agreedPrice) as totalServiceValue,
                sum(appointment.commissionAmount) as totalCommission
            from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:professionalId is null or appointment.professional.id = :professionalId)
            """)
    CompletedAppointmentsTotalsRow sumCompletedByEstablishment(
            @Param("establishmentId") Long establishmentId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("professionalId") Long professionalId);
}
