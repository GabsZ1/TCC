package br.com.salonhub.repository;

import br.com.salonhub.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Declara as consultas de autenticação usadas pela camada de serviço. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Links ainda não usados do usuário (para invalidá-los quando um novo é gerado ou a senha é trocada). */
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);
}
