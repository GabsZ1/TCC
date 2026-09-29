package br.com.salonhub.repository;

import br.com.salonhub.entity.EstablishmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface EstablishmentProfileRepository extends JpaRepository<EstablishmentProfile, Long> {
    Optional<EstablishmentProfile> findByEstablishmentId(Long establishmentId);
    List<EstablishmentProfile> findByEstablishmentIdIn(Collection<Long> establishmentIds);
}

