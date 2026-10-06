package br.com.salonhub.repository;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByEstablishmentIdAndRoleAndActiveTrue(Long establishmentId, UserRole role);

    long countByEstablishmentIdAndRoleAndActiveTrue(Long establishmentId, UserRole role);

    List<User> findByEstablishmentIdAndRole(Long establishmentId, UserRole role);

    Optional<User> findByIdAndEstablishmentIdAndRole(Long id, Long establishmentId, UserRole role);

    Optional<User> findByIdAndEstablishmentIdAndRoleAndActiveTrue(
            Long id, Long establishmentId, UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** Usado a cada requisição autenticada para conferir se a sessão do token ainda vale. */
    @Query("select u.sessionVersion from User u where u.id = :id")
    Optional<Integer> findSessionVersionById(@Param("id") Long id);

    @Query("""
            select distinct u from User u left join u.offeredServices s
            where u.establishment.id = :establishmentId
              and u.role = :role and u.active = true
              and (:serviceId is null or u.offersAllServices = true or s.id = :serviceId)
            """)
    List<User> findActiveProfessionalsForService(
            @Param("establishmentId") Long establishmentId,
            @Param("role") UserRole role,
            @Param("serviceId") Long serviceId);
}
