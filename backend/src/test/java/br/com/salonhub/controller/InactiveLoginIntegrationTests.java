package br.com.salonhub.controller;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US 2.4 CT04 / 2.17 CT06 — login de funcionário desativado. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InactiveLoginIntegrationTests {

    private static final String PASSWORD = "senha-funcionario";

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void shouldTellADeactivatedEmployeeWhyTheLoginWasRefusedOnlyWithTheRightPassword() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", fixture.professional().getId())
                        .with(jwt().jwt(token -> token.subject(fixture.administrator().getId().toString()))))
                .andExpect(status().isOk());

        login(fixture.professional().getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Funcionário está desativado"));
        // Senha errada: não revela que a conta existe nem que está desativada.
        login(fixture.professional().getEmail(), "senha-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void shouldLetTheEmployeeLoginAgainAfterReactivation() throws Exception {
        Fixture fixture = fixture();
        String adminId = fixture.administrator().getId().toString();

        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", fixture.professional().getId())
                        .with(jwt().jwt(token -> token.subject(adminId))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", fixture.professional().getId())
                        .with(jwt().jwt(token -> token.subject(adminId))))
                .andExpect(status().isOk());

        login(fixture.professional().getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldKeepTheGenericMessageForUnknownEmails() throws Exception {
        login("ninguem-" + UUID.randomUUID() + "@login.test", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private Fixture fixture() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão login " + unique).cnpj(cnpj).build());
        User administrator = userRepository.saveAndFlush(User.builder()
                .name("Admin").email("admin-" + unique + "@login.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.ADMIN).establishment(establishment).build());
        User professional = userRepository.saveAndFlush(User.builder()
                .name("Profissional").email("prof-" + unique + "@login.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.PROFESSIONAL).establishment(establishment).build());
        return new Fixture(administrator, professional);
    }

    private record Fixture(User administrator, User professional) {
    }
}
