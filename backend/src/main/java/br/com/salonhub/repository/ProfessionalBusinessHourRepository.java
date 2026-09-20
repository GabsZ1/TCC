package br.com.salonhub.repository;

import br.com.salonhub.entity.ProfessionalBusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface ProfessionalBusinessHourRepository extends JpaRepository<ProfessionalBusinessHour, Long> {
    List<ProfessionalBusinessHour> findByProfessionalIdOrderByDayOfWeek(Long professionalId);
    Optional<ProfessionalBusinessHour> findByProfessionalIdAndDayOfWeek(Long professionalId, DayOfWeek dayOfWeek);
}
