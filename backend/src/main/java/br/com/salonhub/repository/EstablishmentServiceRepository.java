package br.com.salonhub.repository;

import br.com.salonhub.entity.EstablishmentService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EstablishmentServiceRepository extends JpaRepository<EstablishmentService, Long> {

    List<EstablishmentService> findByEstablishmentIdAndActiveTrue(Long establishmentId);

    Optional<EstablishmentService> findByIdAndEstablishmentId(Long id, Long establishmentId);

    Optional<EstablishmentService> findByIdAndEstablishmentIdAndActiveTrue(Long id, Long establishmentId);

    List<EstablishmentService> findByIdInAndEstablishmentIdAndActiveTrue(Set<Long> ids, Long establishmentId);
}
