package br.com.salonhub.config;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SystemAdministratorBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.system-admin.name:}")
    private String name;

    @Value("${app.system-admin.email:}")
    private String email;

    @Value("${app.system-admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (name.isBlank() && email.isBlank() && password.isBlank()) {
            return;
        }
        if (name.isBlank() || email.isBlank() || password.length() < 6) {
            throw new IllegalStateException(
                    "Configure SYSTEM_ADMIN_NAME, SYSTEM_ADMIN_EMAIL e uma SYSTEM_ADMIN_PASSWORD com ao menos 6 caracteres.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.SYSTEM_ADMIN || existing.getEstablishment() != null) {
                throw new IllegalStateException("O e-mail configurado para o administrador do sistema já pertence a outra conta.");
            }
        }, () -> userRepository.save(User.builder()
                .name(name.trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(password))
                .role(UserRole.SYSTEM_ADMIN)
                .active(true)
                .passwordInitialized(true)
                .build()));
    }
}
