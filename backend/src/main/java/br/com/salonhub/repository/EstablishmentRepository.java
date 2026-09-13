package br.com.salonhub.repository;

import br.com.salonhub.entity.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    boolean existsByCnpj(String cnpj);
}