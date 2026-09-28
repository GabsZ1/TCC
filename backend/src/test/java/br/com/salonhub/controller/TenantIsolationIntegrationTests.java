package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantIsolationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldNotAllowAdministratorToUpdateServiceFromAnotherEstablishment() throws Exception {
        String firstAdminToken = registerAndLogin(
                "admin.one@salonhub.test", "11111111000191", "Estabelecimento Um");
        String secondAdminToken = registerAndLogin(
                "admin.two@salonhub.test", "22222222000191", "Estabelecimento Dois");

        MvcResult createdService = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(firstAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(servicePayload("Corte")))
                .andExpect(status().isCreated())
                .andReturn();

        Number serviceId = JsonPath.read(createdService.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/servicos/{id}", serviceId.longValue())
                        .header("Authorization", bearer(secondAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(servicePayload("Alteração indevida")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/servicos/{id}", serviceId.longValue())
                        .header("Authorization", bearer(secondAdminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotAllowAdministratorToManageEmployeeFromAnotherEstablishment() throws Exception {
        String firstAdminToken = registerAndLogin(
                "admin.three@salonhub.test", "33333333000191", "Estabelecimento Três");
        String secondAdminToken = registerAndLogin(
                "admin.four@salonhub.test", "44444444000191", "Estabelecimento Quatro");

        MvcResult createdEmployee = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(firstAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Profissional Um", "professional.one@salonhub.test")))
                .andExpect(status().isCreated())
                .andReturn();

        Number employeeId = JsonPath.read(createdEmployee.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(secondAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alteração indevida",
                                  "email": "changed@salonhub.test",
                                  "occupation": "Cabeleireiro(a)",
                                  "cpf": "%s",
                                  "hireDate": "2025-01-02"
                                }
                                """.formatted(TestDocuments.cpf())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(secondAdminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldForbidProfessionalFromAdministrativeOperations() throws Exception {
        String adminToken = registerAndLogin(
                "admin.five@salonhub.test", "55555555000191", "Estabelecimento Cinco");

        MvcResult createdProfessional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Profissional Bloqueado", "professional.blocked@salonhub.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstAccessToken").isNotEmpty())
                .andReturn();

        String firstAccessToken = JsonPath.read(
                createdProfessional.getResponse().getContentAsString(), "$.firstAccessToken");
        completeFirstAccess(firstAccessToken, "senha123");

        String professionalToken = login("professional.blocked@salonhub.test");

        mockMvc.perform(get("/api/funcionarios")
                        .header("Authorization", bearer(professionalToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(professionalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(servicePayload("Serviço indevido")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
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
                  "occupation": "Cabeleireiro(a)",
                  "cpf": "%s",
                  "hireDate": "2025-01-02"
                }
                """.formatted(name, email, TestDocuments.cpf());
    }

    private String servicePayload(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Descrição",
                  "price": 50.00,
                  "commissionPercentage": 10.00,
                  "durationMinutes": 60
                }
                """.formatted(name);
    }

    private void completeFirstAccess(String token, String password) throws Exception {
        mockMvc.perform(post("/api/auth/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, password)))
                .andExpect(status().isNoContent());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
