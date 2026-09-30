package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerBookingIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void administratorCanBookForOfflineCustomerAndSeeHistory() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                {"ownerName":"Admin balcão","ownerEmail":"admin.balcao@salonhub.test",
                 "password":"senha123","establishmentName":"Salão Balcão","cnpj":"66777888000181"}
                """)).andExpect(status().isCreated());
        String token = JsonPath.read(mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin.balcao@salonhub.test\",\"password\":\"senha123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
        String auth = "Bearer " + token;

        String employeeBody = mvc.perform(post("/api/funcionarios").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Profissional balcão\",\"email\":\"prof.balcao@salonhub.test\","
                        + "\"occupation\":\"Cabeleireiro(a)\",\"cpf\":\"%s\",\"hireDate\":\"2025-01-02\"}"
                        .formatted(TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number professionalId = JsonPath.read(employeeBody, "$.id");
        String firstAccessToken = JsonPath.read(employeeBody, "$.firstAccessToken");
        mvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"senha123\",\"passwordConfirmation\":\"senha123\"}"
                        .formatted(firstAccessToken))).andExpect(status().isNoContent());
        Number serviceId = JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Corte balcão\",\"price\":50.00,\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        Number customerId = JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maria do balcão\",\"phone\":\"44999990000\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");
        LocalDate day = LocalDate.now().plusDays(2);
        while (day.getDayOfWeek().getValue() > 5) day = day.plusDays(1);
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customerId.longValue())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT10:00:00\"}"
                        .formatted(serviceId.longValue(), professionalId.longValue(), day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Maria do balcão"))
                .andExpect(jsonPath("$.customerId").value(customerId));
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/agendamentos", customerId.longValue())
                .header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        // Profissional: agenda só na própria agenda.
        Number colleagueId = JsonPath.read(mvc.perform(post("/api/funcionarios").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Colega balcão\",\"email\":\"colega.balcao@salonhub.test\","
                        + "\"occupation\":\"Cabeleireiro(a)\",\"cpf\":\"%s\",\"hireDate\":\"2025-01-02\"}"
                        .formatted(TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String professionalAuth = "Bearer " + JsonPath.read(mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"prof.balcao@salonhub.test\",\"password\":\"senha123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(get("/api/clientes/estabelecimento/me/opcoes-agendamento").header("Authorization", professionalAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionals.length()").value(1))
                .andExpect(jsonPath("$.professionals[0].id").value(professionalId))
                .andExpect(jsonPath("$.services[0].id").value(serviceId));
        mvc.perform(get("/api/clientes/estabelecimento/me/opcoes-agendamento").header("Authorization", auth))
                .andExpect(jsonPath("$.professionals.length()").value(2));
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customerId.longValue())
                .header("Authorization", professionalAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT14:00:00\"}"
                        .formatted(serviceId.longValue(), colleagueId.longValue(), day)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customerId.longValue())
                .header("Authorization", professionalAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT14:00:00\"}"
                        .formatted(serviceId.longValue(), professionalId.longValue(), day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professionalId").value(professionalId));

        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                {"ownerName":"Admin outro salão","ownerEmail":"outro.balcao@salonhub.test",
                 "password":"senha123","establishmentName":"Outro Salão","cnpj":"11444777000161"}
                """)).andExpect(status().isCreated());
        String otherToken = JsonPath.read(mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"outro.balcao@salonhub.test\",\"password\":\"senha123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/agendamentos", customerId.longValue())
                .header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
    }
}
