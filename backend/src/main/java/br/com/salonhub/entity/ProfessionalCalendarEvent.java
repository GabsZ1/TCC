package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** Representa um evento que ocupa um intervalo da agenda profissional. */
@Entity
@Table(name = "professional_calendar_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfessionalCalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private User professional;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Cria um evento que ocupa um período da agenda profissional. */
    public static ProfessionalCalendarEvent create(
            Establishment establishment, User professional, String title, String description,
            LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay) {
        ProfessionalCalendarEvent event = new ProfessionalCalendarEvent();
        event.establishment = establishment;
        event.professional = professional;
        event.update(title, description, startsAt, endsAt, allDay);
        return event;
    }

    /** Altera período, descrição e modo de dia inteiro do evento. */
    public void update(String title, String description, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay) {
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.allDay = allDay;
    }
}
