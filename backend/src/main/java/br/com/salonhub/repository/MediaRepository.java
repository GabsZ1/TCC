package br.com.salonhub.repository;

import br.com.salonhub.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

/** Declara as consultas de mídia usadas pela camada de serviço. */
public interface MediaRepository extends JpaRepository<Media, Long> {
}
