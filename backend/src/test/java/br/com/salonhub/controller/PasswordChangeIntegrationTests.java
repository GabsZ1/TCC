package br.com.salonhub.controller;

import br.com.salonhub.support.TestJwt;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 2.19 — Alterar senha (CT01 a CT05), para todos os perfis autenticados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordChangeIntegrationTests {

    private static final String CURRENT_PASSWORD = "senha-atual-123";
    private static final String NEW_PASSWORD = "senha-nova-456";

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void shouldChangePasswordAndRejectTheOldOneOnLogin() throws Exception {
        User administrator = createUser(UserRole.ADMIN);

        changePassword(administrator, CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(administrator.getId()));

        userRepository.flush();
        login(administrator.getEmail(), CURRENT_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
        login(administrator.getEmail(), NEW_PASSWORD)
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowProfessionalAndClientToChangeTheirOwnPassword() throws Exception {
        User professional = createUser(UserRole.PROFESSIONAL);
        User client = createUser(UserRole.CLIENT);

        changePassword(professional, CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk());
        changePassword(client, CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk());

        userRepository.flush();
        assertPassword(professional, NEW_PASSWORD);
        assertPassword(client, NEW_PASSWORD);
    }

    @Test
    void shouldRejectWrongCurrentPasswordWithBadRequestInsteadOfUnauthorized() throws Exception {
        User administrator = createUser(UserRole.ADMIN);

        changePassword(administrator, "senha-errada", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Senha atual incorreta"));

        assertPassword(administrator, CURRENT_PASSWORD);
    }

    @Test
    void shouldRejectConfirmationThatDoesNotMatchTheNewPassword() throws Exception {
        User administrator = createUser(UserRole.ADMIN);

        changePassword(administrator, CURRENT_PASSWORD, NEW_PASSWORD, "outra-senha-789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A nova senha e a confirmação não correspondem"));

        assertPassword(administrator, CURRENT_PASSWORD);
    }

    @Test
    void shouldRejectNewPasswordThatDoesNotMeetTheMinimumCriteria() throws Exception {
        User administrator = createUser(UserRole.ADMIN);

        changePassword(administrator, CURRENT_PASSWORD, "123", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword")
                        .value("A senha não atende aos critérios mínimos de segurança (8 a 72 caracteres)"));

        assertPassword(administrator, CURRENT_PASSWORD);
    }

    @Test
    void shouldRejectNewPasswordEqualToTheCurrentOne() throws Exception {
        User administrator = createUser(UserRole.ADMIN);

        changePassword(administrator, CURRENT_PASSWORD, CURRENT_PASSWORD, CURRENT_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A nova senha deve ser diferente da senha atual"));
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(patch("/api/auth/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInactiveUser() throws Exception {
        User professional = createUser(UserRole.PROFESSIONAL);
        professional.deactivate();
        userRepository.saveAndFlush(professional);

        changePassword(professional, CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized());

        assertPassword(professional, CURRENT_PASSWORD);
    }

    private User createUser(UserRole role) {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        Establishment establishment = null;
        if (role == UserRole.ADMIN || role == UserRole.PROFESSIONAL) {
            String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
            establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                    .name("Salão senha " + unique).cnpj(cnpj).build());
        }
        return userRepository.saveAndFlush(User.builder()
                .name("Usuário " + role)
                .email(role.name().toLowerCase() + "-" + unique + "@senha.test")
                .password(passwordEncoder.encode(CURRENT_PASSWORD))
                .role(role)
                .establishment(establishment)
                .build());
    }

    private ResultActions changePassword(User user, String current, String next, String confirmation) throws Exception {
        return mockMvc.perform(patch("/api/auth/senha")
                .with(TestJwt.as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(current, next, confirmation)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private void assertPassword(User user, String expected) {
        String stored = userRepository.findById(user.getId()).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches(expected, stored)).isTrue();
    }

    private static String body(String current, String next, String confirmation) {
        return """
                {"currentPassword":"%s","newPassword":"%s","newPasswordConfirmation":"%s"}
                """.formatted(current, next, confirmation);
    }
}
