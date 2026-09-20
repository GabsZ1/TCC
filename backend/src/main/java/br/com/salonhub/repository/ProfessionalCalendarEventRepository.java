package br.com.salonhub.repository;

import br.com.salonhub.entity.ProfessionalCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfessionalCalendarEventRepository extends JpaRepository<ProfessionalCalendarEvent, Long> {
    boolean existsByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThan(
            Long professionalId, LocalDateTime periodEnd, LocalDateTime periodStart);
    boolean existsByProfessionalIdAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
            Long professionalId, Long eventId, LocalDateTime periodEnd, LocalDateTime periodStart);
    List<ProfessionalCalendarEvent> findByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long professionalId, LocalDateTime periodEnd, LocalDateTime periodStart);
    Optional<ProfessionalCalendarEvent> findByIdAndProfessionalId(Long id, Long professionalId);
}
