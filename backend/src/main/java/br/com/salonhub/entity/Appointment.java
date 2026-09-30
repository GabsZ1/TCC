package br.com.salonhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private User professional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private EstablishmentService service;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "agreed_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal agreedPrice;

    @Column(name = "commission_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    @Column(name = "commission_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Column(name = "invalidation_reason", length = 500)
    private String invalidationReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Appointment schedule(
            Establishment establishment,
            Customer customer,
            User professional,
            EstablishmentService service,
            LocalDateTime startsAt) {
        Appointment appointment = new Appointment();
        appointment.establishment = establishment;
        appointment.customer = customer;
        appointment.professional = professional;
        appointment.service = service;
        appointment.snapshotService(service);
        appointment.startsAt = startsAt;
        appointment.endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        appointment.status = AppointmentStatus.CONFIRMED;
        return appointment;
    }

    public void reassignCustomer(Customer customer) {
        if (!establishment.getId().equals(customer.getEstablishment().getId())) {
            throw new IllegalArgumentException("O cliente deve pertencer ao mesmo estabelecimento.");
        }
        this.customer = customer;
    }


    public void cancel() {
        this.status = AppointmentStatus.CANCELLED;
    }

    public void cancelByAdministrator(String reason, User administrator, LocalDateTime cancelledAt) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledBy = administrator;
        this.cancelledAt = cancelledAt;
    }

    /** Agendamento futuro cancelado porque o cliente excluiu a própria conta (US 2.26). */
    public void cancelBecauseClientAccountWasClosed(LocalDateTime cancelledAt) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = "Conta do cliente excluída";
        this.cancelledAt = cancelledAt;
    }

    public void complete(LocalDateTime completedAt) {
        this.status = AppointmentStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void markNoShow() {
        this.status = AppointmentStatus.NO_SHOW;
    }

    public void invalidate(String reason) {
        this.status = AppointmentStatus.INVALIDATED;
        this.invalidationReason = reason;
    }

    /** Muda horário e/ou profissional mantendo o serviço e os valores combinados (snapshot). */
    public void reschedule(User professional, LocalDateTime startsAt) {
        this.professional = professional;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plusMinutes(durationMinutes);
    }

    public void update(EstablishmentService service, User professional, LocalDateTime startsAt) {
        this.service = service;
        snapshotService(service);
        this.professional = professional;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plusMinutes(service.getDurationMinutes());
    }

    @jakarta.persistence.PrePersist
    void ensureServiceSnapshot() {
        if (serviceName == null) {
            snapshotService(service);
        }
    }

    private void snapshotService(EstablishmentService service) {
        this.serviceName = service.getName();
        this.agreedPrice = service.getPrice();
        this.commissionPercentage = service.getCommissionPercentage();
        this.commissionAmount = agreedPrice.multiply(commissionPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        this.durationMinutes = service.getDurationMinutes();
    }
}
