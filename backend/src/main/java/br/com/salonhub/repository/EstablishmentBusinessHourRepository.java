package br.com.salonhub.repository;

import br.com.salonhub.entity.EstablishmentBusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface EstablishmentBusinessHourRepository extends JpaRepository<EstablishmentBusinessHour, Long> {
    List<EstablishmentBusinessHour> findByEstablishmentIdOrderByDayOfWeek(Long establishmentId);
    Optional<EstablishmentBusinessHour> findByEstablishmentIdAndDayOfWeek(Long establishmentId, DayOfWeek dayOfWeek);
}
