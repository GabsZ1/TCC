package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "establishment_business_hours", uniqueConstraints =
        @UniqueConstraint(columnNames = {"establishment_id", "day_of_week"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EstablishmentBusinessHour {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    public void update(boolean closed, LocalTime openingTime, LocalTime closingTime) {
        this.closed = closed;
        this.openingTime = closed ? null : openingTime;
        this.closingTime = closed ? null : closingTime;
    }
}
