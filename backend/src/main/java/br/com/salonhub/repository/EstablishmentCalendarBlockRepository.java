package br.com.salonhub.repository;

import br.com.salonhub.entity.EstablishmentCalendarBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Declara as consultas de estabelecimentos usadas pela camada de serviço. */
public interface EstablishmentCalendarBlockRepository extends JpaRepository<EstablishmentCalendarBlock, Long> {
    List<EstablishmentCalendarBlock> findByEstablishmentIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Long establishmentId, LocalDateTime end, LocalDateTime start);
    boolean existsByEstablishmentIdAndStartsAtLessThanAndEndsAtGreaterThan(
            Long establishmentId, LocalDateTime end, LocalDateTime start);
    Optional<EstablishmentCalendarBlock> findByIdAndEstablishmentId(Long id, Long establishmentId);
}
