package br.com.salonhub.service.auth;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.auth.FirstAccessRequest;
import br.com.salonhub.dto.auth.LoginRequest;
import br.com.salonhub.dto.auth.LoginResponse;
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
                .filter(User::isActive)
                .filter(User::isPasswordInitialized)
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("E-mail ou senha inválidos");
        }

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
                .filter(candidate -> !candidate.isPasswordInitialized())
                .orElseThrow(() -> new InvalidRequestException(
                        "Token de primeiro acesso inválido, expirado ou já utilizado."));
        user.initializePassword(passwordEncoder.encode(request.password()));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));
        return AuthenticatedUserResponse.fromEntity(user);
    }
}
