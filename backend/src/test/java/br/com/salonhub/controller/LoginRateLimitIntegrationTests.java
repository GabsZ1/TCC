package br.com.salonhub.controller;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proteção contra força bruta no login e abuso dos formulários públicos.
 * Nos outros testes a proteção fica desligada (src/test/resources/config/application.properties);
 * aqui ela é ligada com limites pequenos. Cada teste usa um IP e e-mails próprios, porque os
 * contadores ficam em memória e não voltam atrás com o rollback da transação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=true",
        "app.security.login.max-failures-per-email=5",
        "app.security.login.max-failures-per-ip=8",
        "app.security.public-forms.max-requests-per-ip=3"
})
class LoginRateLimitIntegrationTests {

    private static final String PASSWORD = "Senha@123";
    private static final String LOGIN_BLOCKED = "Muitas tentativas de login. Tente novamente em 15 minuto(s).";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void shouldBlockTheEmailOnTheFifthWrongPasswordEvenForTheRightPasswordAfterwards() throws Exception {
        String ip = uniqueIp();
        User client = client();

        for (int attempt = 1; attempt <= 4; attempt++) {
            login(client.getEmail(), "senha-errada", ip)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
        }
        login(client.getEmail(), "senha-errada", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.message").value(LOGIN_BLOCKED));

        login(client.getEmail(), PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", startsWith("Muitas tentativas de login.")));
        // O bloqueio é do e-mail: vale também a partir de outra rede.
        login(client.getEmail(), PASSWORD, uniqueIp())
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void shouldBlockUnknownEmailsTheSameWaySoTheLockDoesNotRevealWhichAccountsExist() throws Exception {
        String ip = uniqueIp();
        String unknownEmail = "ninguem-" + UUID.randomUUID() + "@limite.test";

        for (int attempt = 1; attempt <= 4; attempt++) {
            login(unknownEmail, PASSWORD, ip).andExpect(status().isUnauthorized());
        }
        login(unknownEmail, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(LOGIN_BLOCKED));
    }

    @Test
    void shouldResetTheEmailCounterAfterASuccessfulLogin() throws Exception {
        String ip = uniqueIp();
        User client = client();

        for (int attempt = 1; attempt <= 4; attempt++) {
            login(client.getEmail(), "senha-errada", ip).andExpect(status().isUnauthorized());
        }
        login(client.getEmail(), PASSWORD, ip).andExpect(status().isOk());

        for (int attempt = 1; attempt <= 3; attempt++) {
            login(client.getEmail(), "senha-errada", ip).andExpect(status().isUnauthorized());
        }
        login(client.getEmail(), PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void shouldBlockAnIpThatTriesManyDifferentEmails() throws Exception {
        String ip = uniqueIp();

        for (int attempt = 1; attempt <= 7; attempt++) {
            login("teste-" + attempt + "-" + UUID.randomUUID() + "@limite.test", PASSWORD, ip)
                    .andExpect(status().isUnauthorized());
        }
        login("teste-8-" + UUID.randomUUID() + "@limite.test", PASSWORD, ip)
                .andExpect(status().isTooManyRequests());

        // Nem uma conta válida entra a partir desse IP enquanto durar o bloqueio...
        User client = client();
        login(client.getEmail(), PASSWORD, ip).andExpect(status().isTooManyRequests());
        // ...mas a mesma conta continua entrando de outra rede.
        login(client.getEmail(), PASSWORD, uniqueIp()).andExpect(status().isOk());
    }

    @Test
    void shouldNotCountTheRightPasswordOfADeactivatedAccountAsAFailure() throws Exception {
        String ip = uniqueIp();
        User client = userRepository.saveAndFlush(User.builder()
                .name("Cliente inativo").email("inativo-" + UUID.randomUUID() + "@limite.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.CLIENT).active(false).build());

        for (int attempt = 1; attempt <= 6; attempt++) {
            login(client.getEmail(), PASSWORD, ip)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Esta conta está desativada."));
        }
    }

    @Test
    void shouldLimitPasswordRecoveryRequestsPerIp() throws Exception {
        String ip = uniqueIp();

        for (int attempt = 1; attempt <= 3; attempt++) {
            requestPasswordReset("ninguem-" + UUID.randomUUID() + "@limite.test", ip)
                    .andExpect(status().isAccepted());
        }
        requestPasswordReset("ninguem-" + UUID.randomUUID() + "@limite.test", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", startsWith("Muitas solicitações feitas a partir desta rede.")));

        requestPasswordReset("ninguem-" + UUID.randomUUID() + "@limite.test", uniqueIp())
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldLimitClientRegistrationsPerIp() throws Exception {
        String ip = uniqueIp();

        for (int attempt = 1; attempt <= 3; attempt++) {
            registerClient(ip).andExpect(status().isCreated());
        }
        registerClient(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", startsWith("Muitas solicitações feitas a partir desta rede.")));
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions requestPasswordReset(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/recuperar-senha")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(email)));
    }

    private ResultActions registerClient(String ip) throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String phone = "4499" + String.format("%07d", Math.floorMod(unique.hashCode(), 10_000_000));
        return mockMvc.perform(post("/api/auth/clientes")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Cliente %s","email":"cliente-%s@limite.test","phone":"%s",
                         "password":"%s","passwordConfirmation":"%s"}
                        """.formatted(unique, unique, phone, PASSWORD, PASSWORD)));
    }

    private User client() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.builder()
                .name("Cliente").email("cliente-" + unique + "@limite.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.CLIENT).build());
    }

    private static String uniqueIp() {
        UUID random = UUID.randomUUID();
        return "10.%d.%d.%d".formatted(
                Math.floorMod(random.getMostSignificantBits(), 250) + 1,
                Math.floorMod(random.getLeastSignificantBits(), 250) + 1,
                Math.floorMod(random.hashCode(), 250) + 1);
    }
}
