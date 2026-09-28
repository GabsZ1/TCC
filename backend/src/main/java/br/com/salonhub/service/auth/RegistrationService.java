package br.com.salonhub.service.auth;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.auth.ClientRegistrationRequest;
import br.com.salonhub.dto.auth.InitialRegistrationRequest;
import br.com.salonhub.dto.auth.InitialRegistrationResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.EstablishmentProfile;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.validation.PhoneNumbers;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentProfileRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentProfileRepository profileRepository;
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

        Establishment establishment = establishmentRepository.save(
                Establishment.builder()
                        .name(request.establishmentName().trim())
                        .cnpj(cnpj)
                        .email(normalizeOptionalText(request.establishmentEmail()))
                        .build());

        String establishmentPhone = normalizeOptionalText(request.establishmentPhone());
        if (establishmentPhone != null) {
            String normalizedPhone = establishmentPhone.replaceAll("[^0-9+]", "");
            if (!normalizedPhone.isBlank()) {
                profileRepository.save(EstablishmentProfile.builder()
                        .establishment(establishment)
                        .phone(normalizedPhone)
                        .build());
            }
        }

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
        if (!request.password().equals(request.passwordConfirmation())) {
            throw new InvalidRequestException("As senhas não coincidem!");
        }
        String phone = PhoneNumbers.normalize(request.phone());
        if (phone == null) {
            throw new InvalidRequestException("Informe um telefone válido!");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail já cadastrado");
        }

        User client = userRepository.save(
                User.builder()
                        .name(request.name().trim())
                        .email(email)
                        .password(passwordEncoder.encode(request.password()))
                        .phone(phone)
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
                        .openingTime(LocalTime.of(9, 0))
                        .closingTime(LocalTime.of(18, 0))
                        .build())
                .toList());
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
