package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Guarda o token temporário usado na redefinição de senha. */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    public PasswordResetToken(User user, String tokenHash, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Indica se o link já foi consumido e não pode ser reutilizado. */
    public boolean isUsed() {
        return usedAt != null;
    }

    /** Compara a expiração do link ao relógio informado. */
    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** Marca o link como consumido para impedir uma segunda redefinição. */
    public void markUsed(OffsetDateTime now) {
        this.usedAt = now;
    }
}
