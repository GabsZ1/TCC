package br.com.salonhub.controller;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre a RN 2.3.2 do TCC: funcionário sem atendimentos vinculados é removido
 * permanentemente; funcionário com atendimentos vinculados é apenas desativado
 * (e continua aparecendo na listagem, com status inativo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeRemovalIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldReactivateOnlyAnEmployeeFromTheAdministratorsEstablishment() throws Exception {
        String firstAdminEmail = "admin.reactivation.first@salonhub.test";
        String firstAdminToken = registerAndLogin(
                firstAdminEmail, "30303030000138", "Salão Reativação Um");
        String secondAdminToken = registerAndLogin(
                "admin.reactivation.second@salonhub.test", "40404040000113", "Salão Reativação Dois");

        User firstAdmin = userRepository.findByEmailIgnoreCase(firstAdminEmail).orElseThrow();
        User inactiveEmployee = userRepository.saveAndFlush(User.builder()
                .name("Funcionário Inativo")
                .email("inactive.reactivation@salonhub.test")
                .password("senha-codificada-para-o-teste")
                .role(UserRole.PROFESSIONAL)
                .establishment(firstAdmin.getEstablishment())
                .active(false)
                .build());

        mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(secondAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Tentativa de transferência", inactiveEmployee.getEmail())))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", inactiveEmployee.getId())
                        .header("Authorization", bearer(secondAdminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", inactiveEmployee.getId())
                        .header("Authorization", bearer(firstAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.establishmentName").value("Salão Reativação Um"));

        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", inactiveEmployee.getId())
                        .header("Authorization", bearer(firstAdminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldPermanentlyRemoveEmployeeWithoutAppointments() throws Exception {
        String adminToken = registerAndLogin(
                "admin.removal.clean@salonhub.test", "10101010000177", "Salão Remoção Limpa");

        MvcResult created = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Funcionário Sem Vínculo", "sem.vinculo@salonhub.test")))
                .andExpect(status().isCreated())
                .andReturn();
        Number employeeId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedPermanently").value(true));

        mockMvc.perform(get("/api/funcionarios")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldDeactivateInsteadOfRemoveEmployeeWithAppointments() throws Exception {
        String adminToken = registerAndLogin(
                "admin.removal.linked@salonhub.test", "20202020000152", "Salão Remoção Vinculada");

        MvcResult employeeResult = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Funcionário Com Vínculo", "com.vinculo@salonhub.test")))
                .andExpect(status().isCreated())
                .andReturn();
        Number employeeId = JsonPath.read(employeeResult.getResponse().getContentAsString(), "$.id");

        MvcResult serviceResult = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Corte Remoção",
                                  "price": 40.00,
                                  "durationMinutes": 30
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number serviceId = JsonPath.read(serviceResult.getResponse().getContentAsString(), "$.id");

        MvcResult establishmentResult = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        Number establishmentId = JsonPath.read(
                establishmentResult.getResponse().getContentAsString(), "$.establishmentId");

        mockMvc.perform(post("/api/auth/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cliente Remoção",
                                  "email": "cliente.removal@salonhub.test",
                                  "password": "senha123"
                                }
                                """))
                .andExpect(status().isCreated());
        String clientToken = login("cliente.removal@salonhub.test");

        LocalDateTime startsAt = LocalDate.now().plusDays(1).atTime(10, 0);
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": %d,
                                  "professionalId": %d,
                                  "startsAt": "%s"
                                }
                                """.formatted(serviceId.longValue(), employeeId.longValue(), startsAt)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedPermanently").value(false));

        mockMvc.perform(get("/api/funcionarios")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));

        // CT05: tentar remover de novo não deve alterar nada, só informar que já está desativado.
        mockMvc.perform(delete("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    private String registerAndLogin(String email, String cnpj, String establishmentName) throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador",
                                  "ownerEmail": "%s",
                                  "password": "senha123",
                                  "establishmentName": "%s",
                                  "cnpj": "%s"
                                }
                                """.formatted(email, establishmentName, cnpj)))
                .andExpect(status().isCreated());

        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "senha123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String employeePayload(String name, String email) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "senha123"
                }
                """.formatted(name, email);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
