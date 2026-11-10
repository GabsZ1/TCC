package br.com.salonhub.controller;

import br.com.salonhub.support.TestJwt;
import br.com.salonhub.support.TestDocuments;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica a ativação da conta do funcionário no primeiro acesso. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeFirstAccessIntegrationTests extends FirstAccessIntegrationSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void shouldLetOnlyTheEmployeeDefineTheInitialPasswordAndThenLogin() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Primeiro acesso").cnpj(cnpj).build());
        User administrator = userRepository.saveAndFlush(User.builder()
                .name("Admin").email("admin-" + unique + "@test.local")
                .password(passwordEncoder.encode("senha-admin"))
                .role(UserRole.ADMIN).establishment(establishment).build());
        String employeeEmail = "employee-" + unique + "@test.local";

        String creationBody = mockMvc.perform(post("/api/funcionarios")
                        .with(TestJwt.as(administrator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Funcionário","email":"%s","occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                                """.formatted(employeeEmail, TestDocuments.cpf())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstAccessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String firstAccessToken = firstAccessToken(creationBody);
        User employee = userRepository.findByEmailIgnoreCase(employeeEmail).orElseThrow();
        assertThat(employee.isPasswordInitialized()).isFalse();
        assertThat(passwordEncoder.matches("senha-funcionario", employee.getPassword())).isFalse();

        login(employeeEmail, "senha-funcionario").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", "Bearer " + firstAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Indevido","email":"indevido@test.local","occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                                """.formatted(TestDocuments.cpf())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"senha-funcionario"}
                                """.formatted(firstAccessToken)))
                .andExpect(status().isNoContent());

        userRepository.flush();
        userRepository.findById(employee.getId()).ifPresent(user ->
                assertThat(user.isPasswordInitialized()).isTrue());
        login(employeeEmail, "senha-funcionario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PROFESSIONAL"));

        mockMvc.perform(post("/api/auth/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"outra-senha"}
                                """.formatted(firstAccessToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendInvalidatesPreviousInvitationAndRestrictsTenantAndInitializedUsers() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        Establishment first = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Convites A").cnpj(String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L))).build());
        Establishment second = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Convites B").cnpj(String.format("%014d", Math.floorMod(System.nanoTime() + 1, 100_000_000_000_000L))).build());
        User admin = userRepository.saveAndFlush(User.builder().name("Admin A").email("admin-a-" + unique + "@test.local")
                .password(passwordEncoder.encode("senha-admin")).role(UserRole.ADMIN).establishment(first).build());
        User outsider = userRepository.saveAndFlush(User.builder().name("Admin B").email("admin-b-" + unique + "@test.local")
                .password(passwordEncoder.encode("senha-admin")).role(UserRole.ADMIN).establishment(second).build());
        String creation = mockMvc.perform(post("/api/funcionarios")
                .with(TestJwt.as(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Convidado","email":"guest-%s@test.local","occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                        """.formatted(unique, TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(creation, "$.id")).longValue();
        String oldToken = firstAccessToken(creation);

        mockMvc.perform(post("/api/funcionarios/{id}/reenviar-convite", id)
                .with(TestJwt.as(outsider)))
                .andExpect(status().isNotFound());
        String resent = mockMvc.perform(post("/api/funcionarios/{id}/reenviar-convite", id)
                .with(TestJwt.as(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newToken = firstAccessToken(resent);
        assertThat(newToken).isNotEqualTo(oldToken);
        String secondResend = mockMvc.perform(post("/api/funcionarios/{id}/reenviar-convite", id)
                .with(TestJwt.as(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String latestToken = firstAccessToken(secondResend);
        assertThat(latestToken).isNotEqualTo(newToken);

        mockMvc.perform(post("/api/auth/recuperar-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"guest-%s@test.local\"}".formatted(unique)))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"senha-funcionario\"}".formatted(oldToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"senha-funcionario\"}".formatted(newToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"senha-funcionario\"}".formatted(latestToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"outra-senha\"}".formatted(latestToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/funcionarios/{id}/reenviar-convite", id)
                .with(TestJwt.as(admin)))
                .andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }
}
