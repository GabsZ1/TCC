package br.com.salonhub.repository;

import br.com.salonhub.entity.EstablishmentService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EstablishmentServiceRepository extends JpaRepository<EstablishmentService, Long> {

    List<EstablishmentService> findByEstablishmentId(Long establishmentId);

    Page<EstablishmentService> findByEstablishmentId(Long establishmentId, Pageable pageable);

    @Query("""
            select count(s) > 0 from EstablishmentService s
            where s.establishment.id = :establishmentId
              and lower(trim(s.name)) = lower(trim(:name))
              and (:excludedId is null or s.id <> :excludedId)
            """)
    boolean existsWithName(Long establishmentId, String name, Long excludedId);

    List<EstablishmentService> findByEstablishmentIdAndActiveTrue(Long establishmentId);

    Optional<EstablishmentService> findByIdAndEstablishmentId(Long id, Long establishmentId);

    Optional<EstablishmentService> findByIdAndEstablishmentIdAndActiveTrue(Long id, Long establishmentId);

    List<EstablishmentService> findByIdInAndEstablishmentIdAndActiveTrue(Set<Long> ids, Long establishmentId);
}
