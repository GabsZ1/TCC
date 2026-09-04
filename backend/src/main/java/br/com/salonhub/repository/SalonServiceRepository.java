package br.com.salonhub.repository;

import br.com.salonhub.entity.SalonService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalonServiceRepository extends JpaRepository<SalonService, Long> {

    List<SalonService> findByEstablishmentIdAndActiveTrue(Long establishmentId);

    Optional<SalonService> findByIdAndEstablishmentId(Long id, Long establishmentId);
}
