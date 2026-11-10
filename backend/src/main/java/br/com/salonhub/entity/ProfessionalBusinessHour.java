package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Representa a jornada semanal específica de um profissional. */
@Entity
@Table(name = "professional_business_hours", uniqueConstraints =
        @UniqueConstraint(columnNames = {"professional_id", "day_of_week"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfessionalBusinessHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private User professional;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    /** Cria a configuração de jornada para um dia da semana. */
    public static ProfessionalBusinessHour create(
            User professional, DayOfWeek dayOfWeek, boolean closed,
            LocalTime openingTime, LocalTime closingTime) {
        ProfessionalBusinessHour hour = new ProfessionalBusinessHour();
        hour.professional = professional;
        hour.dayOfWeek = dayOfWeek;
        hour.update(closed, openingTime, closingTime);
        return hour;
    }

    /** Altera a jornada do dia, inclusive herança do expediente e fechamento. */
    public void update(boolean closed, LocalTime openingTime, LocalTime closingTime) {
        this.closed = closed;
        this.openingTime = closed ? null : openingTime;
        this.closingTime = closed ? null : closingTime;
    }
}
