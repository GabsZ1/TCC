package br.com.salonhub.repository;

import br.com.salonhub.entity.ProfessionalBusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

/** Declara as consultas de agenda e atuação dos profissionais usadas pela camada de serviço. */
public interface ProfessionalBusinessHourRepository extends JpaRepository<ProfessionalBusinessHour, Long> {
    List<ProfessionalBusinessHour> findByProfessionalIdOrderByDayOfWeek(Long professionalId);
    Optional<ProfessionalBusinessHour> findByProfessionalIdAndDayOfWeek(Long professionalId, DayOfWeek dayOfWeek);
    List<ProfessionalBusinessHour> findByProfessionalIdInAndDayOfWeek(Iterable<Long> professionalIds, DayOfWeek dayOfWeek);
}
