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
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Appointment schedule(
            Establishment establishment,
            User client,
            User professional,
            EstablishmentService service,
            LocalDateTime startsAt) {
        Appointment appointment = new Appointment();
        appointment.establishment = establishment;
        appointment.client = client;
        appointment.professional = professional;
        appointment.service = service;
        appointment.snapshotService(service);
        appointment.startsAt = startsAt;
        appointment.endsAt = startsAt.plusMinutes(service.getDurationMinutes());
        appointment.status = AppointmentStatus.CONFIRMED;
        return appointment;
    }

    public void cancel() {
        this.status = AppointmentStatus.CANCELLED;
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
