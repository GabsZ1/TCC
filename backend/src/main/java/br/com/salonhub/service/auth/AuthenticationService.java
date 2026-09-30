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

    @Transactional(readOnly = true)
    public LoginResponse authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(User::isPasswordInitialized)
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("E-mail ou senha inválidos");
        }
        // US 2.4 CT04 / 2.17 CT06: o motivo específico só aparece depois de a senha
        // conferir, para não revelar a terceiros quais e-mails existem ou estão inativos.
        if (!user.isActive()) {
            throw new UnauthorizedException(user.getRole() == UserRole.PROFESSIONAL
                    ? "Funcionário está desativado"
                    : "Esta conta está desativada.");
        }
        requireEstablishmentLoginAllowed(user);

        TokenService.GeneratedToken token = tokenService.generate(user);
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                AuthenticatedUserResponse.fromEntity(user));
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
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
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
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));
        requireEstablishmentLoginAllowed(user);
        return AuthenticatedUserResponse.fromEntity(user);
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
