package br.com.salonhub.service.auth;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.auth.ClientRegistrationRequest;
import br.com.salonhub.dto.auth.InitialRegistrationRequest;
import br.com.salonhub.dto.auth.InitialRegistrationResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public InitialRegistrationResponse registerEstablishment(InitialRegistrationRequest request) {
        String cnpj = digitsOnly(request.cnpj());
        String ownerEmail = normalizeEmail(request.ownerEmail());

        if (establishmentRepository.existsByCnpj(cnpj)) {
            throw new ConflictException("CNPJ já cadastrado");
        }
        if (userRepository.existsByEmailIgnoreCase(ownerEmail)) {
            throw new ConflictException("E-mail já cadastrado");
        }

        int minimumAdvanceMinutes = (request.minimumAdvanceHours() == null
                ? 2
                : request.minimumAdvanceHours()) * 60;
        int maximumAdvanceDays = request.maximumAdvanceDays() == null ? 60 : request.maximumAdvanceDays();
        validateAdvancePolicy(minimumAdvanceMinutes, maximumAdvanceDays);

        Establishment establishment = establishmentRepository.save(
                Establishment.builder()
                        .name(request.establishmentName().trim())
                        .cnpj(cnpj)
                        .email(normalizeOptionalText(request.establishmentEmail()))
                        .phone(normalizeOptionalText(request.establishmentPhone()))
                        .minimumAdvanceMinutes(minimumAdvanceMinutes)
                        .maximumAdvanceDays(maximumAdvanceDays)
                        .build());

        createDefaultBusinessHours(establishment);
        User administrator = userRepository.save(
                User.builder()
                        .establishment(establishment)
                        .name(request.ownerName().trim())
                        .email(ownerEmail)
                        .password(passwordEncoder.encode(request.password()))
                        .role(UserRole.ADMIN)
                        .build());

        return new InitialRegistrationResponse(
                establishment.getId(),
                establishment.getName(),
                administrator.getId(),
                administrator.getName(),
                administrator.getEmail());
    }

    @Transactional
    public AuthenticatedUserResponse registerClient(ClientRegistrationRequest request) {
        String email = normalizeEmail(request.email());
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
                        .build());
        return AuthenticatedUserResponse.fromEntity(client);
    }

    private void createDefaultBusinessHours(Establishment establishment) {
        businessHourRepository.saveAll(Arrays.stream(DayOfWeek.values())
                .map(day -> EstablishmentBusinessHour.builder()
                        .establishment(establishment)
                        .dayOfWeek(day)
                        .closed(false)
                        .openingTime(establishment.getOpeningTime())
                        .closingTime(establishment.getClosingTime())
                        .build())
                .toList());
    }

    private void validateAdvancePolicy(int minimumMinutes, int maximumDays) {
        if (minimumMinutes > maximumDays * 1440) {
            throw new InvalidRequestException(
                    "A antecedência mínima não pode ser maior que a janela máxima de agendamento.");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
