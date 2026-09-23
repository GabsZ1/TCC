package br.com.salonhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "establishments")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Establishment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean paused = false;

    @Column(name = "paused_by_system", nullable = false)
    @Builder.Default
    private boolean pausedBySystem = false;

    @Column(name = "bookings_blocked", nullable = false)
    @Builder.Default
    private boolean bookingsBlocked = false;

    @Column(name = "bookings_blocked_by_system", nullable = false)
    @Builder.Default
    private boolean bookingsBlockedBySystem = false;

    @Column(name = "minimum_advance_minutes", nullable = false)
    @Builder.Default
    private Integer minimumAdvanceMinutes = 120;

    @Column(name = "maximum_advance_days", nullable = false)
    @Builder.Default
    private Integer maximumAdvanceDays = 60;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void updateRegistration(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void updateAppointmentAdvancePolicy(int minimumAdvanceMinutes, int maximumAdvanceDays) {
        this.minimumAdvanceMinutes = minimumAdvanceMinutes;
        this.maximumAdvanceDays = maximumAdvanceDays;
    }

    public void reactivate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void pause(boolean bySystemAdministrator) {
        this.paused = true;
        if (bySystemAdministrator) {
            this.pausedBySystem = true;
        }
    }

    public void resume() {
        this.paused = false;
        this.pausedBySystem = false;
    }

    public void blockBookings(boolean bySystemAdministrator) {
        this.bookingsBlocked = true;
        if (bySystemAdministrator) {
            this.bookingsBlockedBySystem = true;
        }
    }

    public void allowBookings() {
        this.bookingsBlocked = false;
        this.bookingsBlockedBySystem = false;
    }

    public boolean acceptsNewAppointments() {
        return active && !paused && !bookingsBlocked;
    }

}
