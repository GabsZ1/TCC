package br.com.salonhub.service.auth;

import br.com.salonhub.dto.auth.PasswordRecoveryRequest;
import br.com.salonhub.dto.auth.PasswordRecoveryResponse;
import br.com.salonhub.dto.auth.PasswordResetRequest;
import br.com.salonhub.entity.PasswordResetToken;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.exception.TooManyRequestsException;
import br.com.salonhub.notification.EmailNotifier;
import br.com.salonhub.notification.FrontendLinks;
import br.com.salonhub.repository.PasswordResetTokenRepository;
import br.com.salonhub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * US 2.20 — Recuperar senha.
 * <ul>
 *   <li>O link é único, expira em {@code app.password-reset.expiration-minutes} (padrão 15) e não pode ser reutilizado.</li>
 *   <li>O mesmo e-mail só pode pedir um novo link a cada {@code app.password-reset.cooldown-seconds} (padrão 120 — RNF.02.01).</li>
 *   <li>Um novo pedido invalida os links anteriores ainda não usados.</li>
 *   <li>O banco guarda só o hash SHA-256 do token.</li>
 * </ul>
 */
@Service
public class PasswordRecoveryService {

    static final String INVALID_LINK = "Este link não é mais válido";
    static final String EXPIRED_LINK = "O link de recuperação expirou! Solicite um novo link";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNotifier emailNotifier;
    private final FrontendLinks frontendLinks;
    private final Clock clock;
    private final long expirationMinutes;
    private final long cooldownSeconds;

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailNotifier emailNotifier,
            FrontendLinks frontendLinks,
            Clock clock,
            @Value("${app.password-reset.expiration-minutes:15}") long expirationMinutes,
            @Value("${app.password-reset.cooldown-seconds:120}") long cooldownSeconds) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailNotifier = emailNotifier;
        this.frontendLinks = frontendLinks;
        this.clock = clock;
        this.expirationMinutes = expirationMinutes;
        this.cooldownSeconds = cooldownSeconds;
    }

    @Transactional
    public PasswordRecoveryResponse requestReset(PasswordRecoveryRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Long userId = userRepository.findByEmailIgnoreCase(email)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Não existe cadastro com o e-mail informado."));
        // Trava o usuário para que dois pedidos simultâneos não furem o intervalo mínimo.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Não existe cadastro com o e-mail informado."));

        if (!user.isActive()) {
            throw new InvalidRequestException("Esta conta está desativada. Procure o administrador do estabelecimento.");
        }
        if (!user.isPasswordInitialized()) {
            throw new InvalidRequestException(
                    "Esta conta ainda não concluiu o primeiro acesso. Use o link de primeiro acesso enviado por e-mail.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        requireCooldownElapsed(user.getId(), now);

        invalidatePendingTokens(user.getId(), now);
        String rawToken = SecureTokens.generate();
        tokenRepository.save(new PasswordResetToken(
                user, SecureTokens.sha256(rawToken), now, now.plusMinutes(expirationMinutes)));

        emailNotifier.sendPasswordReset(
                user.getEmail(), user.getName(), frontendLinks.passwordReset(rawToken), expirationMinutes);

        return new PasswordRecoveryResponse(
                "Enviamos um link de recuperação para o seu e-mail. Ele é válido por "
                        + expirationMinutes + " minutos.",
                expirationMinutes);
    }

    /** Usado pela tela de redefinição ao abrir, para avisar logo se o link já não serve. */
    @Transactional(readOnly = true)
    public void validateToken(String rawToken) {
        requireUsable(findToken(rawToken, false), OffsetDateTime.now(clock));
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PasswordResetToken token = requireUsable(findToken(request.token(), true), now);

        if (!request.newPassword().equals(request.newPasswordConfirmation())) {
            throw new InvalidRequestException("A nova senha e a confirmação não correspondem");
        }

        User user = token.getUser();
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        token.markUsed(now);
        // Grava a nova senha e o uso do token na mesma transação (RNF.05.02 da 2.16 / 2.20)
        // e invalida qualquer outro link pendente do mesmo usuário.
        invalidatePendingTokens(user.getId(), now);
    }

    /**
     * Marca como usados os links pendentes do usuário. É feito entidade por entidade
     * (e não com um UPDATE em massa) para manter o contexto de persistência
     * consistente: um UPDATE em massa com limpeza automática deixava o usuário
     * "desanexado" e a troca de senha seguinte não era gravada.
     */
    private void invalidatePendingTokens(Long userId, OffsetDateTime now) {
        tokenRepository.findByUserIdAndUsedAtIsNull(userId).forEach(pending -> pending.markUsed(now));
    }

    private Optional<PasswordResetToken> findToken(String rawToken, boolean forUpdate) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = SecureTokens.sha256(rawToken.trim());
        return forUpdate ? tokenRepository.findByTokenHashForUpdate(hash) : tokenRepository.findByTokenHash(hash);
    }

    private PasswordResetToken requireUsable(Optional<PasswordResetToken> candidate, OffsetDateTime now) {
        PasswordResetToken token = candidate.orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        if (token.isUsed() || !token.getUser().isActive()) {
            throw new InvalidRequestException(INVALID_LINK);
        }
        if (token.isExpired(now)) {
            throw new InvalidRequestException(EXPIRED_LINK);
        }
        return token;
    }

    private void requireCooldownElapsed(Long userId, OffsetDateTime now) {
        tokenRepository.findTopByUserIdOrderByCreatedAtDesc(userId).ifPresent(last -> {
            OffsetDateTime allowedAt = last.getCreatedAt().plusSeconds(cooldownSeconds);
            if (now.isBefore(allowedAt)) {
                long waitSeconds = Math.max(1, Duration.between(now, allowedAt).toSeconds());
                throw new TooManyRequestsException(
                        "Um link de recuperação já foi enviado. Aguarde " + waitSeconds
                                + " segundos para solicitar outro.");
            }
        });
    }
}
