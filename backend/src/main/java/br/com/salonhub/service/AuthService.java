package br.com.salonhub.service;

import br.com.salonhub.dto.auth.InitialRegistrationRequest;
import br.com.salonhub.dto.auth.InitialRegistrationResponse;
import br.com.salonhub.dto.auth.LoginRequest;
import br.com.salonhub.dto.auth.LoginResponse;
import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.auth.ClientRegistrationRequest;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.UnauthorizedException;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.DayOfWeek;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public InitialRegistrationResponse register(InitialRegistrationRequest request) {
        String cnpj = digitsOnly(request.cnpj());
        String ownerEmail = request.ownerEmail().trim().toLowerCase(Locale.ROOT);

        if (establishmentRepository.existsByCnpj(cnpj)) {
            throw new ConflictException("CNPJ já cadastrado");
        }
        if (userRepository.existsByEmailIgnoreCase(ownerEmail)) {
            throw new ConflictException("E-mail já cadastrado");
        }

        Establishment establishment = establishmentRepository.save(
                Establishment.builder()
                        .name(request.establishmentName().trim())
                        .cnpj(cnpj)
                        .email(normalizeOptionalText(request.establishmentEmail()))
                        .phone(normalizeOptionalText(request.establishmentPhone()))
                        .minimumAdvanceMinutes((request.minimumAdvanceHours() == null ? 2 : request.minimumAdvanceHours()) * 60)
                        .maximumAdvanceDays(request.maximumAdvanceDays() == null ? 60 : request.maximumAdvanceDays())
                        .build()
        );

        validateAdvancePolicy(establishment.getMinimumAdvanceMinutes(), establishment.getMaximumAdvanceDays());

        businessHourRepository.saveAll(Arrays.stream(DayOfWeek.values())
                .map(day -> EstablishmentBusinessHour.builder()
                        .establishment(establishment)
                        .dayOfWeek(day)
                        .closed(false)
                        .openingTime(establishment.getOpeningTime())
                        .closingTime(establishment.getClosingTime())
                        .build())
                .toList());

        User administrator = userRepository.save(
                User.builder()
                        .establishment(establishment)
                        .name(request.ownerName().trim())
                        .email(ownerEmail)
                        .password(passwordEncoder.encode(request.password()))
                        .role(UserRole.ADMIN)
                        .build()
        );

        return new InitialRegistrationResponse(
                establishment.getId(),
                establishment.getName(),
                administrator.getId(),
                administrator.getName(),
                administrator.getEmail()
        );
    }

    @Transactional
    public AuthenticatedUserResponse registerClient(ClientRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail já cadastrado");
        }

        User client = userRepository.save(
                User.builder()
                        .name(request.name().trim())
                        .email(email)
                        .password(passwordEncoder.encode(request.password()))
                        .role(UserRole.CLIENT)
                        .active(true)
                        .build()
        );

        return AuthenticatedUserResponse.fromEntity(client);
    }

    @Transactional(readOnly = true)
    public LoginResponse authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("E-mail ou senha inválidos");
        }

        TokenService.GeneratedToken token = tokenService.generate(user);
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                AuthenticatedUserResponse.fromEntity(user)
        );
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));
        return AuthenticatedUserResponse.fromEntity(user);
    }

    private String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateAdvancePolicy(int minimumMinutes, int maximumDays) {
        if (minimumMinutes > maximumDays * 1440) {
            throw new br.com.salonhub.exception.InvalidRequestException(
                    "A antecedência mínima não pode ser maior que a janela máxima de agendamento.");
        }
    }
}
