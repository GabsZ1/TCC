package br.com.salonhub.repository;

import br.com.salonhub.entity.EstablishmentGalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EstablishmentGalleryPhotoRepository extends JpaRepository<EstablishmentGalleryPhoto, Long> {
    List<EstablishmentGalleryPhoto> findByEstablishmentIdOrderByDisplayOrderAscIdAsc(Long establishmentId);
    Optional<EstablishmentGalleryPhoto> findByIdAndEstablishmentId(Long id, Long establishmentId);
    long countByEstablishmentId(Long establishmentId);
}
