package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.entity.Customer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerBookingIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ignoredPairsCascadeWhenMergeDeletesTheirCustomer() throws Exception {
        long establishmentId = registerEstablishment("admin.cascade@salonhub.test", "66777888000181");
        String admin = login("admin.cascade@salonhub.test");
        long source = createCustomer(admin, "Origem", "44999990000", null);
        long separate = createCustomer(admin, "Pessoa distinta", "44999990000", null);
        long survivor = createCustomer(admin, "Sobrevivente", "44999990000", null);
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/manter-separados/{otherId}", source, separate)
                .header("Authorization", admin)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(1, ignoredPairCount(source));

        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/mesclar/{otherId}", survivor, source)
                .header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(survivor));
        org.junit.jupiter.api.Assertions.assertFalse(customers.existsById(source));
        org.junit.jupiter.api.Assertions.assertEquals(0, ignoredPairCount(source));
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/possiveis-duplicados", survivor)
                .header("Authorization", admin)).andExpect(jsonPath("$[0].id").value(separate));
        org.junit.jupiter.api.Assertions.assertEquals(2, customers.findByEstablishmentIdOrderByNameAsc(establishmentId).size());
    }

    @Test
    void staffReceivesTenantScopedSuggestionsAndCanStillCreateAnotherCustomer() throws Exception {
        long establishmentId = registerEstablishment("admin.suggest@salonhub.test", "66777888000181");
        String admin = login("admin.suggest@salonhub.test");
        long existing = createCustomer(admin, "Maria da Silva", "44999990000", "MARIA@EXAMPLE.COM");
        registerEstablishment("other.suggest@salonhub.test", "11444777000161");
        createCustomer(login("other.suggest@salonhub.test"), "Outro salão", "44999990000", "maria@example.com");
        mvc.perform(get("/api/clientes/estabelecimento/me/sugestoes").header("Authorization", admin)
                .param("phone", "(44) 99999-0000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(existing));
        mvc.perform(get("/api/clientes/estabelecimento/me/sugestoes").header("Authorization", admin)
                .param("email", " maria@example.com "))
                .andExpect(jsonPath("$[0].id").value(existing));
        createCustomer(admin, "Maria diferente", "44999990000", null);
        org.junit.jupiter.api.Assertions.assertEquals(2, customers.findByEstablishmentIdOrderByNameAsc(establishmentId).size());
    }

    private int ignoredPairCount(long customerId) {
        return jdbc.queryForObject("""
                select count(*) from customer_duplicate_ignores
                where customer_id_a = ? or customer_id_b = ?
                """, Integer.class, customerId, customerId);
    }

    @Test
    void onlineBookingIsNotBlockedAndMergePreservesHistory() throws Exception {
        long establishmentId = registerEstablishment("admin.link@salonhub.test", "66777888000181");
        String admin = login("admin.link@salonhub.test");
        String employee = mvc.perform(post("/api/funcionarios").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Profissional vínculo","email":"prof.link@salonhub.test",
                 "occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                """.formatted(TestDocuments.cpf()))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long professionalId = ((Number) JsonPath.read(employee, "$.id")).longValue();
        mvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"senha123\",\"passwordConfirmation\":\"senha123\"}"
                        .formatted((String) JsonPath.read(employee, "$.firstAccessToken")))).andExpect(status().isNoContent());
        long serviceId = ((Number) JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Corte vínculo\",\"price\":50,\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long customerId = createCustomer(admin, "Maria", "44999990000", "MARIA.LINK@SALONHUB.TEST");
        LocalDate day = LocalDate.now().plusDays(2);
        while (day.getDayOfWeek().getValue() > 5) day = day.plusDays(1);
        String booking = "{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT10:00:00\"}"
                .formatted(serviceId, professionalId, day);
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customerId)
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").value(customerId));
        registerClient("maria.link@salonhub.test", "44999990000");
        String client = login("maria.link@salonhub.test");
        mvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId)
                .header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content(booking.replace("T10:00:00", "T14:00:00")))
                .andExpect(status().isCreated());
        long onlineId = customers.findByEstablishmentIdOrderByNameAsc(establishmentId).stream()
                .filter(customer -> customer.getUser() != null).findFirst().orElseThrow().getId();
        org.junit.jupiter.api.Assertions.assertEquals(2, customers.findByEstablishmentIdOrderByNameAsc(establishmentId).size());
        org.junit.jupiter.api.Assertions.assertNull(customers.findById(customerId).orElseThrow().getUser());
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/agendamentos", customerId)
                .header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));

        String professional = login("prof.link@salonhub.test");
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/possiveis-duplicados", onlineId)
                .header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(customerId)).andExpect(jsonPath("$[0].appointmentCount").value(1));
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/mesclar/{otherId}", onlineId, customerId)
                .header("Authorization", professional)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/manter-separados/{otherId}", onlineId, customerId)
                .header("Authorization", professional)).andExpect(status().isForbidden());
        registerEstablishment("other.link@salonhub.test", "11444777000161");
        String otherAdmin = login("other.link@salonhub.test");
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/possiveis-duplicados", customerId)
                .header("Authorization", otherAdmin)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/mesclar/{otherId}", onlineId, customerId)
                .header("Authorization", otherAdmin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/agendamentos/me").header("Authorization", client))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].customerId").value(onlineId));
        mvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId)
                .header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content(booking.replace("T10:00:00", "T16:00:00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").value(onlineId));
        org.junit.jupiter.api.Assertions.assertEquals(2, customers.findByEstablishmentIdOrderByNameAsc(establishmentId).size());
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/mesclar/{otherId}", customerId, onlineId)
                .header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(onlineId));
        org.junit.jupiter.api.Assertions.assertEquals(1, customers.findByEstablishmentIdOrderByNameAsc(establishmentId).size());
        org.junit.jupiter.api.Assertions.assertFalse(customers.existsById(customerId));
        mvc.perform(get("/api/agendamentos/me").header("Authorization", client))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void matchingAndSeparateDecisionsAreTenantScopedAndSymmetric() throws Exception {
        long establishmentId = registerEstablishment("admin.phone@salonhub.test", "66777888000181");
        String admin = login("admin.phone@salonhub.test");
        long customerId = createCustomer(admin, "Cliente telefone", "44999990000", null);
        long similar = createCustomer(admin, "Outro telefone", "44999990000", "Different@Example.com");
        long byEmail = createCustomer(admin, "Mesmo email", "44988880000", "different@example.com");
        registerEstablishment("other.phone@salonhub.test", "11444777000161");
        String otherAdmin = login("other.phone@salonhub.test");
        createCustomer(otherAdmin, "Outro tenant", "44999990000", "different@example.com");
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/possiveis-duplicados", similar)
                .header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/manter-separados/{otherId}", similar, customerId)
                .header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/manter-separados/{otherId}", customerId, similar)
                .header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/possiveis-duplicados", similar)
                .header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(byEmail));
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/possiveis-duplicados", customerId)
                .header("Authorization", admin)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/manter-separados/{otherId}", similar, byEmail)
                .header("Authorization", otherAdmin)).andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(3, customers.findByEstablishmentIdOrderByNameAsc(establishmentId).size());
    }

    @Test
    void differentAccountsCannotBeMergedAndFormattedPhoneIsDetected() throws Exception {
        long establishmentId = registerEstablishment("admin.two@salonhub.test", "66777888000181");
        String admin = login("admin.two@salonhub.test");
        long firstId = createCustomer(admin, "Primeiro", "44999990000", "FIRST@EXAMPLE.COM");
        Customer first = customers.findById(firstId).orElseThrow();
        Customer formatted = Customer.create(first.getEstablishment(), "Formatado", "(44) 99999-0000", " first@example.com ");
        customers.saveAndFlush(formatted);
        mvc.perform(get("/api/clientes/estabelecimento/me/{id}/possiveis-duplicados", firstId)
                .header("Authorization", admin)).andExpect(jsonPath("$[0].id").value(formatted.getId()));
        org.junit.jupiter.api.Assertions.assertEquals("44999990000", formatted.getPhone());
        org.junit.jupiter.api.Assertions.assertEquals("first@example.com", formatted.getEmail());

        registerClient("first.user@salonhub.test", "44988880000");
        registerClient("second.user@salonhub.test", "44977770000");
        long secondId = createCustomer(admin, "Segundo", "44999990000", null);
        first.link(users.findByEmailIgnoreCase("first.user@salonhub.test").orElseThrow());
        customers.findById(secondId).orElseThrow()
                .link(users.findByEmailIgnoreCase("second.user@salonhub.test").orElseThrow());
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/mesclar/{otherId}", firstId, secondId)
                .header("Authorization", admin)).andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(3, customers.findByEstablishmentIdOrderByNameAsc(establishmentId).size());
    }

    private long registerEstablishment(String email, String cnpj) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Admin\",\"ownerEmail\":\"%s\",\"password\":\"senha123\",\"establishmentName\":\"Salão\",\"cnpj\":\"%s\"}"
                        .formatted(email, cnpj))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.establishmentId")).longValue();
    }

    private String login(String email) throws Exception {
        return "Bearer " + JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"senha123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private void registerClient(String email, String phone) throws Exception {
        mvc.perform(post("/api/auth/clientes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maria\",\"email\":\"%s\",\"phone\":\"%s\",\"password\":\"senha123\",\"passwordConfirmation\":\"senha123\"}"
                        .formatted(email, phone))).andExpect(status().isCreated());
    }

    private long createCustomer(String auth, String name, String phone, String email) throws Exception {
        String emailJson = email == null ? "null" : "\"" + email + "\"";
        return ((Number) JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"phone\":\"%s\",\"email\":%s}"
                        .formatted(name, phone, emailJson))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

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
        mvc.perform(get("/api/clientes/estabelecimento/me/sugestoes")
                .header("Authorization", professionalAuth).param("phone", "44999990000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(customerId));
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
