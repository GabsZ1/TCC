package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.PasswordResetToken;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.notification.EmailNotifier;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.PasswordResetTokenRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.auth.SecureTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 2.20 — Recuperar senha (CT01 a CT04 + regras de negócio) e envio do
 * e-mail de primeiro acesso ao cadastrar funcionário (US 2.1 CT02).
 * O envio real por SMTP é substituído por um mock para inspecionar os links.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordRecoveryIntegrationTests {

    private static final String CURRENT_PASSWORD = "senha-atual-123";
    private static final String NEW_PASSWORD = "senha-nova-456";
    private static final String RESET_LINK_PREFIX = "http://localhost:5173/redefinir-senha?token=";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @MockitoBean private EmailNotifier emailNotifier;

    @Test
    void shouldEmailAResetLinkAndStoreOnlyTheTokenHash() throws Exception {
        User client = createUser(UserRole.CLIENT, true, true);

        requestReset(client.getEmail().toUpperCase())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresInMinutes").value(15))
                .andExpect(jsonPath("$.message").isNotEmpty());

        List<String> links = capturedResetLinks(client.getEmail(), 1);
        assertThat(links.getFirst()).startsWith(RESET_LINK_PREFIX);
        String rawToken = tokenFrom(links.getFirst());

        assertThat(tokenRepository.findByTokenHash(rawToken)).isEmpty();
        PasswordResetToken stored = tokenRepository.findByTokenHash(SecureTokens.sha256(rawToken)).orElseThrow();
        assertThat(stored.getUser().getId()).isEqualTo(client.getId());
        assertThat(stored.getExpiresAt()).isAfter(stored.getCreatedAt().plusMinutes(14));
        assertThat(stored.getExpiresAt()).isBeforeOrEqualTo(stored.getCreatedAt().plusMinutes(15));
    }

    @Test
    void shouldInformThatTheEmailIsNotRegistered() throws Exception {
        requestReset("ninguem-" + UUID.randomUUID() + "@recuperacao.test")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Não existe cadastro com o e-mail informado."));

        verify(emailNotifier, never()).sendPasswordReset(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRejectInvalidEmailFormat() throws Exception {
        requestReset("email-sem-arroba.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("Informe um e-mail válido"));
    }

    @Test
    void shouldAllowOnlyOneRequestPerCooldownPeriod() throws Exception {
        User administrator = createUser(UserRole.ADMIN, true, true);

        requestReset(administrator.getEmail()).andExpect(status().isAccepted());
        requestReset(administrator.getEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Aguarde")));

        verify(emailNotifier, times(1)).sendPasswordReset(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldResetThePasswordThroughTheLinkAndLoginWithTheNewOne() throws Exception {
        User professional = createUser(UserRole.PROFESSIONAL, true, true);
        requestReset(professional.getEmail()).andExpect(status().isAccepted());
        String rawToken = tokenFrom(capturedResetLinks(professional.getEmail(), 1).getFirst());

        validate(rawToken).andExpect(status().isNoContent());
        reset(rawToken, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(professional.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
        login(professional.getEmail(), CURRENT_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotAllowReusingTheSameLink() throws Exception {
        User client = createUser(UserRole.CLIENT, true, true);
        requestReset(client.getEmail()).andExpect(status().isAccepted());
        String rawToken = tokenFrom(capturedResetLinks(client.getEmail(), 1).getFirst());

        reset(rawToken, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        reset(rawToken, "outra-senha-789", "outra-senha-789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este link não é mais válido"));
        validate(rawToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este link não é mais válido"));
        assertPassword(client, NEW_PASSWORD);
    }

    @Test
    void shouldRejectAnExpiredLink() throws Exception {
        User client = createUser(UserRole.CLIENT, true, true);
        String rawToken = SecureTokens.generate();
        OffsetDateTime now = OffsetDateTime.now();
        tokenRepository.saveAndFlush(new PasswordResetToken(
                client, SecureTokens.sha256(rawToken), now.minusMinutes(30), now.minusMinutes(15)));

        validate(rawToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O link de recuperação expirou! Solicite um novo link"));
        reset(rawToken, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O link de recuperação expirou! Solicite um novo link"));
        assertPassword(client, CURRENT_PASSWORD);
    }

    @Test
    void shouldRejectUnknownLink() throws Exception {
        validate("token-que-nao-existe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este link não é mais válido"));
    }

    @Test
    void shouldRejectConfirmationDifferentFromTheNewPasswordAndKeepTheLinkUsable() throws Exception {
        User client = createUser(UserRole.CLIENT, true, true);
        requestReset(client.getEmail()).andExpect(status().isAccepted());
        String rawToken = tokenFrom(capturedResetLinks(client.getEmail(), 1).getFirst());

        reset(rawToken, NEW_PASSWORD, "outra-senha-789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A nova senha e a confirmação não correspondem"));
        reset(rawToken, "123", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").isNotEmpty());

        validate(rawToken).andExpect(status().isNoContent());
        assertPassword(client, CURRENT_PASSWORD);
    }

    @Test
    void shouldInvalidateThePreviousLinkWhenANewOneIsRequested() throws Exception {
        User client = createUser(UserRole.CLIENT, true, true);
        requestReset(client.getEmail()).andExpect(status().isAccepted());
        String firstToken = tokenFrom(capturedResetLinks(client.getEmail(), 1).getFirst());

        // Simula que o intervalo mínimo entre pedidos já passou.
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET created_at = created_at - INTERVAL '5 minutes' WHERE user_id = ?",
                client.getId());
        entityManager.clear();

        requestReset(client.getEmail()).andExpect(status().isAccepted());
        String secondToken = tokenFrom(capturedResetLinks(client.getEmail(), 2).getLast());

        reset(firstToken, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este link não é mais válido"));
        reset(secondToken, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void shouldNotResetPasswordOfAUserDeactivatedAfterTheRequest() throws Exception {
        User professional = createUser(UserRole.PROFESSIONAL, true, true);
        requestReset(professional.getEmail()).andExpect(status().isAccepted());
        String rawToken = tokenFrom(capturedResetLinks(professional.getEmail(), 1).getFirst());

        User reloaded = userRepository.findById(professional.getId()).orElseThrow();
        reloaded.deactivate();
        userRepository.saveAndFlush(reloaded);

        reset(rawToken, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este link não é mais válido"));
    }

    @Test
    void shouldNotSendLinksToDeactivatedAccountsOrPendingFirstAccess() throws Exception {
        User inactive = createUser(UserRole.PROFESSIONAL, false, true);
        User pending = createUser(UserRole.PROFESSIONAL, true, false);

        requestReset(inactive.getEmail()).andExpect(status().isBadRequest());
        requestReset(pending.getEmail()).andExpect(status().isBadRequest());

        verify(emailNotifier, never()).sendPasswordReset(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldEmailTheFirstAccessLinkWhenAnEmployeeIsCreated() throws Exception {
        User administrator = createUser(UserRole.ADMIN, true, true);
        String employeeEmail = "convite-" + UUID.randomUUID().toString().substring(0, 8) + "@recuperacao.test";

        String body = mockMvc.perform(post("/api/funcionarios")
                        .with(jwt().jwt(token -> token.subject(administrator.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Funcionária Convite","email":"%s","occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                                """.formatted(employeeEmail, TestDocuments.cpf())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstAccessToken = JsonPath.read(body, "$.firstAccessToken");
        verify(emailNotifier).sendFirstAccessInvitation(
                eq(employeeEmail),
                eq("Funcionária Convite"),
                eq(administrator.getEstablishment().getName()),
                eq("http://localhost:5173/primeiro-acesso?token="
                        + URLEncoder.encode(firstAccessToken, StandardCharsets.UTF_8)),
                any(Instant.class));
    }

    private User createUser(UserRole role, boolean active, boolean passwordInitialized) {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        Establishment establishment = null;
        if (role == UserRole.ADMIN || role == UserRole.PROFESSIONAL) {
            String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
            establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                    .name("Salão recuperação " + unique).cnpj(cnpj).build());
        }
        return userRepository.saveAndFlush(User.builder()
                .name("Usuário " + role)
                .email(role.name().toLowerCase() + "-" + unique + "@recuperacao.test")
                .password(passwordEncoder.encode(CURRENT_PASSWORD))
                .role(role)
                .active(active)
                .passwordInitialized(passwordInitialized)
                .establishment(establishment)
                .build());
    }

    private List<String> capturedResetLinks(String email, int expectedCalls) {
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(emailNotifier, times(expectedCalls))
                .sendPasswordReset(eq(email), anyString(), links.capture(), eq(15L));
        return links.getAllValues();
    }

    private static String tokenFrom(String link) {
        return URLDecoder.decode(link.substring(link.indexOf("token=") + "token=".length()), StandardCharsets.UTF_8);
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/recuperar-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(email)));
    }

    private ResultActions validate(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/redefinir-senha").param("token", token));
    }

    private ResultActions reset(String token, String password, String confirmation) throws Exception {
        return mockMvc.perform(post("/api/auth/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","newPassword":"%s","newPasswordConfirmation":"%s"}
                        """.formatted(token, password, confirmation)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private void assertPassword(User user, String expected) {
        entityManager.flush();
        entityManager.clear();
        String stored = userRepository.findById(user.getId()).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches(expected, stored)).isTrue();
    }
}
