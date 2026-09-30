package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "establishment_calendar_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstablishmentCalendarBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;
    @Column(nullable = false, length = 200)
    private String reason;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static EstablishmentCalendarBlock create(Establishment establishment, LocalDateTime startsAt,
            LocalDateTime endsAt, String reason) {
        EstablishmentCalendarBlock block = new EstablishmentCalendarBlock();
        block.establishment = establishment;
        block.update(startsAt, endsAt, reason);
        return block;
    }

    public void update(LocalDateTime startsAt, LocalDateTime endsAt, String reason) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason.trim();
    }
}
