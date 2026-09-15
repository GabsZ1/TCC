package br.com.salonhub.repository;

import br.com.salonhub.entity.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    boolean existsByCnpj(String cnpj);

    List<Establishment> findAllByActiveTrueOrderByNameAsc();
}
