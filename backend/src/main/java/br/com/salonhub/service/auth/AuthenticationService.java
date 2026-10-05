package br.com.salonhub.service.auth;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.auth.FirstAccessRequest;
import br.com.salonhub.dto.auth.LoginRequest;
import br.com.salonhub.dto.auth.LoginResponse;
import br.com.salonhub.dto.auth.PasswordChangeRequest;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.UnauthorizedException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthenticationRateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public LoginResponse authenticate(LoginRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        // Bloqueio temporário após tentativas repetidas (força bruta). Vale também para
        // e-mails sem cadastro, para não revelar quais contas existem.
        rateLimiter.checkLoginAllowed(email, clientIp);
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(User::isPasswordInitialized)
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            rateLimiter.registerLoginFailure(email, clientIp);
            throw new UnauthorizedException("E-mail ou senha inválidos");
        }
        rateLimiter.registerLoginSuccess(email);
        // US 2.4 CT04 / 2.17 CT06: o motivo específico só aparece depois de a senha
        // conferir, para não revelar a terceiros quais e-mails existem ou estão inativos.
        if (!user.isActive()) {
            throw new UnauthorizedException(user.getRole() == UserRole.PROFESSIONAL
                    ? "Funcionário está desativado"
                    : "Esta conta está desativada.");
        }
        requireEstablishmentLoginAllowed(user);

        return issueSession(user);
    }

    @Transactional
    public void completeFirstAccess(FirstAccessRequest request) {
        Long userId = tokenService.validateFirstAccess(request.token());
        if (userId == null) {
            throw new InvalidRequestException("Token de primeiro acesso inválido ou expirado.");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .filter(User::isActive)
                .filter(candidate -> candidate.getRole() == UserRole.PROFESSIONAL)
                .filter(this::isEstablishmentLoginAllowed)
                .filter(candidate -> !candidate.isPasswordInitialized())
                .filter(candidate -> java.util.Objects.equals(candidate.getFirstAccessVersion(), tokenService.firstAccessVersion(request.token())))
                .orElseThrow(() -> new InvalidRequestException(
                        "Token de primeiro acesso inválido, expirado ou já utilizado."));
        user.initializePassword(passwordEncoder.encode(request.password()));
    }

    /**
     * US 2.19 — o próprio usuário autenticado troca a senha informando a atual.
     * Erros de validação respondem 400 (e não 401) para que o front não
     * interprete uma senha atual errada como sessão expirada.
     * <p>
     * A troca encerra todas as sessões abertas (inclusive em outros dispositivos) e devolve um
     * token novo, para que o usuário continue conectado no dispositivo em que trocou a senha.
     */
    @Transactional
    public LoginResponse changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));
        requireEstablishmentLoginAllowed(user);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidRequestException("Senha atual incorreta");
        }
        if (!request.newPassword().equals(request.newPasswordConfirmation())) {
            throw new InvalidRequestException("A nova senha e a confirmação não correspondem");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidRequestException("A nova senha deve ser diferente da senha atual");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        return issueSession(user);
    }

    /**
     * Renova o token de uma sessão ainda válida, para quem está usando o sistema não ser
     * desconectado quando o token expira. A sessão continua sendo encerrada pelo front após
     * 30 min de inatividade e por troca ou redefinição de senha.
     */
    @Transactional(readOnly = true)
    public LoginResponse renewSession(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));
        requireEstablishmentLoginAllowed(user);
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));
        requireEstablishmentLoginAllowed(user);
        return AuthenticatedUserResponse.fromEntity(user);
    }

    private LoginResponse issueSession(User user) {
        TokenService.GeneratedToken token = tokenService.generate(user);
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                AuthenticatedUserResponse.fromEntity(user));
    }

    private void requireEstablishmentLoginAllowed(User user) {
        if (!isEstablishmentLoginAllowed(user)) {
            throw new UnauthorizedException("O acesso ao estabelecimento está temporariamente indisponível.");
        }
    }

    private boolean isEstablishmentLoginAllowed(User user) {
        if (user.getEstablishment() == null) {
            return true;
        }
        if (!user.getEstablishment().isActive()) {
            return false;
        }
        return user.getRole() != UserRole.PROFESSIONAL || !user.getEstablishment().isPaused();
    }
}
