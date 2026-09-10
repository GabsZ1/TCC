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

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByEstablishmentIdAndRoleAndActiveTrue(Long establishmentId, UserRole role);

    Optional<User> findByIdAndEstablishmentIdAndRole(Long id, Long establishmentId, UserRole role);

    Optional<User> findByIdAndEstablishmentIdAndRoleAndActiveTrue(
            Long id, Long establishmentId, UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
