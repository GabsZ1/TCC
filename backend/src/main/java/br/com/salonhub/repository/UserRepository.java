package br.com.salonhub.repository;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByEstablishmentIdAndRoleAndActiveTrue(Long establishmentId, UserRole role);

    Optional<User> findByIdAndEstablishmentIdAndRole(Long id, Long establishmentId, UserRole role);
}
