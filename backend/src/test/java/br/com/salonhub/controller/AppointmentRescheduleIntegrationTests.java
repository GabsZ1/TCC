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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Remarcação e troca de profissional pelo administrador. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentRescheduleIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void administratorReschedulesAndReassignsRespectingConflictsAndPermissions() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                {"ownerName":"Admin remarca","ownerEmail":"admin.remarca@salonhub.test",
                 "password":"senha123","establishmentName":"Salão Remarca","cnpj":"%s"}
                """.formatted("71727374000151"))).andExpect(status().isCreated());
        String admin = "Bearer " + login("admin.remarca@salonhub.test");

        String anaBody = createEmployee(admin, "Ana remarca", "ana.remarca@salonhub.test");
        long ana = ((Number) JsonPath.read(anaBody, "$.id")).longValue();
        mvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"senha123\",\"passwordConfirmation\":\"senha123\"}"
                        .formatted((String) JsonPath.read(anaBody, "$.firstAccessToken"))))
                .andExpect(status().isNoContent());
        long bruno = ((Number) JsonPath.read(createEmployee(admin, "Bruno remarca", "bruno.remarca@salonhub.test"), "$.id")).longValue();
        long service = ((Number) JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Corte remarca\",\"price\":50.00,\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long customer = ((Number) JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cliente remarca\",\"phone\":\"44999991111\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long other = ((Number) JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Outra cliente\",\"phone\":\"44999992222\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        LocalDate day = LocalDate.now().plusDays(3);
        long appointment = book(admin, customer, service, ana, day + "T10:00:00");
        book(admin, other, service, bruno, day + "T15:00:00");

        // Troca de profissional e de horário, mantendo serviço e preço combinados.
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, bruno, day + "T13:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(bruno))
                .andExpect(jsonPath("$.startsAt", startsWith(day + "T13:00")))
                .andExpect(jsonPath("$.serviceId").value(service))
                .andExpect(jsonPath("$.agreedPrice").value(50.0));

        // Conflito com outro agendamento do Bruno.
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(service, bruno, day + "T15:00:00")))
                .andExpect(status().isConflict());

        // Profissional não remarca.
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment)
                        .header("Authorization", "Bearer " + login("ana.remarca@salonhub.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, ana, day + "T16:00:00")))
                .andExpect(status().isForbidden());

        // Cancelado não pode ser remarcado.
        mvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, ana, day + "T16:00:00")))
                .andExpect(status().isConflict());
    }

    private String login(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"senha123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String createEmployee(String admin, String name, String email) throws Exception {
        return mvc.perform(post("/api/funcionarios").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"occupation\":\"Cabeleireiro(a)\",\"cpf\":\"%s\",\"hireDate\":\"2025-01-02\"}"
                                .formatted(name, email, TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private long book(String admin, long customer, long service, long professional, String startsAt) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customer)
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%s\"}"
                                .formatted(service, professional, startsAt)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String body(Long serviceId, long professionalId, String startsAt) {
        return "{\"serviceId\":%s,\"professionalId\":%d,\"startsAt\":\"%s\"}"
                .formatted(serviceId == null ? "null" : serviceId.toString(), professionalId, startsAt);
    }
}
