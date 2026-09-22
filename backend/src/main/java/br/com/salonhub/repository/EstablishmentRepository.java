package br.com.salonhub.repository;

import br.com.salonhub.entity.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    boolean existsByCnpj(String cnpj);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Establishment e where e.id = :id")
    Optional<Establishment> findByIdForUpdate(@Param("id") Long id);

    List<Establishment> findAllByActiveTrueAndPausedFalseAndBookingsBlockedFalseOrderByNameAsc();

    List<Establishment> findAllByOrderByNameAsc();
}
