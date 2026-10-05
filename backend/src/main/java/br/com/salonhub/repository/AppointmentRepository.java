package br.com.salonhub.repository;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Agendamentos ainda sem lembrete que começam entre {@code from} (exclusivo) e {@code until}. */
    @EntityGraph(attributePaths = {"establishment", "professional", "customer", "customer.user"})
    @Query("""
            select a from Appointment a
            where a.status = :status and a.reminderSentAt is null
              and a.startsAt > :from and a.startsAt <= :until
              and a.establishment.active = true
            order by a.startsAt
            """)
    List<Appointment> findPendingReminders(@Param("status") AppointmentStatus status,
                                           @Param("from") LocalDateTime from,
                                           @Param("until") LocalDateTime until);

    @Query("""
            select appointment.establishment.id
            from Appointment appointment
            where appointment.id = :id
              and appointment.customer.user.id = :clientId
            """)
    Optional<Long> findEstablishmentIdByIdAndClientId(
            @Param("id") Long id,
            @Param("clientId") Long clientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from Appointment appointment where appointment.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            select appointment
            from Appointment appointment
            where appointment.id = :id
              and appointment.customer.user.id = :clientId
            """)
    Optional<Appointment> findByIdAndClientIdForUpdate(
            @Param("id") Long id,
            @Param("clientId") Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
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

    boolean existsByCustomerIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
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

    boolean existsByCustomerIdAndStatusAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
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

    List<Appointment> findByCustomerUserIdAndStatusAndStartsAtGreaterThanOrderByStartsAtAsc(
            Long clientId, AppointmentStatus status, LocalDateTime startsAt);
    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Page<Appointment> findByCustomerUserIdOrderByStartsAtDescIdDesc(Long clientId, Pageable pageable);
    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Optional<Appointment> findFirstByCustomerUserIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(
            Long clientId, AppointmentStatus status, LocalDateTime startsAt);

    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Page<Appointment> findByCustomerUserIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(
            Long clientId, AppointmentStatus status, LocalDateTime startsAt, Pageable pageable);

    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    @Query(value = """
            select appointment from Appointment appointment where appointment.customer.user.id = :clientId
              and (appointment.status <> br.com.salonhub.entity.AppointmentStatus.CONFIRMED
                   or appointment.startsAt < :now)
            order by appointment.startsAt desc, appointment.id desc
            """, countQuery = """
            select count(appointment) from Appointment appointment where appointment.customer.user.id = :clientId
              and (appointment.status <> br.com.salonhub.entity.AppointmentStatus.CONFIRMED
                   or appointment.startsAt < :now)
            """)
    Page<Appointment> findClientHistory(@Param("clientId") Long clientId, @Param("now") LocalDateTime now,
                                        Pageable pageable);

    List<Appointment> findByCustomerIdAndEstablishmentIdOrderByStartsAtDesc(Long customerId, Long establishmentId);
    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Page<Appointment> findByCustomerIdAndEstablishmentIdOrderByStartsAtDesc(Long customerId, Long establishmentId, Pageable pageable);
    long countByCustomerId(Long customerId);

    Optional<Appointment> findByIdAndCustomerUserId(Long id, Long clientId);

    Optional<Appointment> findByIdAndEstablishmentId(Long id, Long establishmentId);

    List<Appointment> findByProfessionalIdOrderByStartsAtDesc(Long professionalId);
    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Page<Appointment> findByProfessionalIdOrderByStartsAtDescIdDesc(Long professionalId, Pageable pageable);
    long countByProfessionalIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            Long professionalId, AppointmentStatus status, LocalDateTime start, LocalDateTime end);
    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Optional<Appointment> findFirstByProfessionalIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThanOrderByStartsAtAscIdAsc(
            Long professionalId, AppointmentStatus status, LocalDateTime now, LocalDateTime dayEnd);

    List<Appointment> findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long professionalId, LocalDateTime periodEnd, LocalDateTime periodStart);

    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    @Query(value = """
            select appointment
            from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and (cast(:dayStart as LocalDateTime) is null or (
                    appointment.startsAt >= :dayStart
                    and appointment.startsAt < :dayEnd))
              and (:professionalId is null or appointment.professional.id = :professionalId)
              and (:status is null or appointment.status = :status)
            order by appointment.startsAt desc
            """, countQuery = """
            select count(appointment) from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and (cast(:dayStart as LocalDateTime) is null or (
                    appointment.startsAt >= :dayStart and appointment.startsAt < :dayEnd))
              and (:professionalId is null or appointment.professional.id = :professionalId)
              and (:status is null or appointment.status = :status)
            """)
    Page<Appointment> findEstablishmentAgenda(
            @Param("establishmentId") Long establishmentId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("professionalId") Long professionalId,
            @Param("status") AppointmentStatus status,
            Pageable pageable);

    List<Appointment> findByProfessionalIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long professionalId,
            AppointmentStatus status,
            LocalDateTime periodEnd,
            LocalDateTime periodStart
    );

    List<Appointment> findByCustomerUserIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long clientId,
            AppointmentStatus status,
            LocalDateTime periodEnd,
            LocalDateTime periodStart
    );

    boolean existsByEstablishmentIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            Long establishmentId, AppointmentStatus status, LocalDateTime periodEnd, LocalDateTime periodStart);

    List<Appointment> findByCustomerIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long customerId,
            AppointmentStatus status,
            LocalDateTime periodEnd,
            LocalDateTime periodStart
    );

    long countByEstablishmentId(Long establishmentId);
    long countByEstablishmentIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            Long establishmentId, LocalDateTime start, LocalDateTime end);
    long countByEstablishmentIdAndStatusAndEndsAtLessThanEqual(
            Long establishmentId, AppointmentStatus status, LocalDateTime now);

    @Query("""
            select sum(appointment.agreedPrice) from Appointment appointment
            where appointment.establishment.id = :establishmentId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :start and appointment.completedAt < :end
            """)
    BigDecimal sumRevenueCompletedInPeriod(@Param("establishmentId") Long establishmentId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    List<Appointment> findByEstablishmentIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(
            Long establishmentId, AppointmentStatus status, LocalDateTime startsAt, Pageable pageable);

    /** Relatório financeiro: concluídos no período (pela data de conclusão), agrupados por serviço. */
    @Query("""
            select service.id as id, service.name as name, count(appointment) as completedCount,
                   sum(appointment.agreedPrice) as revenue, sum(appointment.commissionAmount) as commission
            from Appointment appointment
            join appointment.service service
            join appointment.professional professional
            where appointment.establishment.id = :establishmentId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:professionalId is null or professional.id = :professionalId)
              and (:serviceId is null or service.id = :serviceId)
            group by service.id, service.name
            order by sum(appointment.agreedPrice) desc, service.name asc
            """)
    List<FinancialReportRow> findFinancialReportByService(
            @Param("establishmentId") Long establishmentId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("professionalId") Long professionalId,
            @Param("serviceId") Long serviceId);

    /** Relatório financeiro: mesmos filtros, agrupados por profissional. */
    @Query("""
            select professional.id as id, professional.name as name, count(appointment) as completedCount,
                   sum(appointment.agreedPrice) as revenue, sum(appointment.commissionAmount) as commission
            from Appointment appointment
            join appointment.service service
            join appointment.professional professional
            where appointment.establishment.id = :establishmentId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:professionalId is null or professional.id = :professionalId)
              and (:serviceId is null or service.id = :serviceId)
            group by professional.id, professional.name
            order by sum(appointment.agreedPrice) desc, professional.name asc
            """)
    List<FinancialReportRow> findFinancialReportByProfessional(
            @Param("establishmentId") Long establishmentId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("professionalId") Long professionalId,
            @Param("serviceId") Long serviceId);

    /** Atendimentos que já terminaram e continuam confirmados (ninguém concluiu nem registrou falta). */
    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Page<Appointment> findByEstablishmentIdAndStatusAndEndsAtLessThanEqualOrderByStartsAtAscIdAsc(
            Long establishmentId, AppointmentStatus status, LocalDateTime endsAt, Pageable pageable);

    @EntityGraph(attributePaths = {"establishment", "service", "professional", "customer", "customer.user", "cancelledBy"})
    Page<Appointment> findByProfessionalIdAndStatusAndEndsAtLessThanEqualOrderByStartsAtAscIdAsc(
            Long professionalId, AppointmentStatus status, LocalDateTime endsAt, Pageable pageable);

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
                sum(case when appointment.status = br.com.salonhub.entity.AppointmentStatus.INVALIDATED then 1L else 0L end) as invalidatedCount,
                sum(case when appointment.status = br.com.salonhub.entity.AppointmentStatus.NO_SHOW then 1L else 0L end) as noShowCount
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
    @Query(value = """
            select appointment
            from Appointment appointment
            join fetch appointment.customer
            join fetch appointment.service
            where appointment.professional.id = :professionalId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:serviceId is null or appointment.service.id = :serviceId)
            order by appointment.completedAt desc, appointment.id desc
            """, countQuery = """
            select count(appointment) from Appointment appointment
            where appointment.professional.id = :professionalId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:serviceId is null or appointment.service.id = :serviceId)
            """)
    Page<Appointment> findCompletedByProfessional(
            @Param("professionalId") Long professionalId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("serviceId") Long serviceId, Pageable pageable);

    @Query("""
            select count(appointment) as totalAppointments,
                   sum(appointment.agreedPrice) as totalServiceValue,
                   sum(appointment.commissionAmount) as totalCommission
            from Appointment appointment
            where appointment.professional.id = :professionalId
              and appointment.status = br.com.salonhub.entity.AppointmentStatus.COMPLETED
              and appointment.completedAt >= :periodStart
              and appointment.completedAt < :periodEnd
              and (:serviceId is null or appointment.service.id = :serviceId)
            """)
    CompletedAppointmentsTotalsRow sumCompletedByProfessional(
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
            join fetch appointment.customer
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
