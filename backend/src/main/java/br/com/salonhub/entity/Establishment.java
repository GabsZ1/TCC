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

/** Representa o estabelecimento e suas regras operacionais de atendimento. */
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

    /** Atualiza os dados cadastrais sem alterar o estado operacional da unidade. */
    public void updateRegistration(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /** Guarda os limites de antecedência usados para oferecer novas reservas. */
    public void updateAppointmentAdvancePolicy(int minimumAdvanceMinutes, int maximumAdvanceDays) {
        this.minimumAdvanceMinutes = minimumAdvanceMinutes;
        this.maximumAdvanceDays = maximumAdvanceDays;
    }

    /** Reabre a unidade preservando suas configurações e seu histórico. */
    public void reactivate() {
        this.active = true;
    }

    /** Interrompe a operação da unidade sem apagar seus dados. */
    public void deactivate() {
        this.active = false;
    }

    /** Guarda a origem da pausa para impedir que a administração local reverta uma ação do sistema. */
    public void pause(boolean bySystemAdministrator) {
        this.paused = true;
        if (bySystemAdministrator) {
            this.pausedBySystem = true;
        }
    }

    /** Remove a pausa operacional e sua marca de origem. */
    public void resume() {
        this.paused = false;
        this.pausedBySystem = false;
    }

    /** Impede novas reservas sem desativar o estabelecimento ou alterar as reservas existentes. */
    public void blockBookings(boolean bySystemAdministrator) {
        this.bookingsBlocked = true;
        if (bySystemAdministrator) {
            this.bookingsBlockedBySystem = true;
        }
    }

    /** Libera novas reservas e limpa a marca de bloqueio global. */
    public void allowBookings() {
        this.bookingsBlocked = false;
        this.bookingsBlockedBySystem = false;
    }

    /** Centraliza os três estados que precisam permitir uma nova reserva. */
    public boolean acceptsNewAppointments() {
        return active && !paused && !bookingsBlocked;
    }

}
