package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerBookingIntegrationTests extends FirstAccessIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void customerListUsesTenantScopedPagesAndSearchesEmail() throws Exception {
        registerEstablishment("admin.pages@salonhub.test", "66777888000181");
        String admin = login("admin.pages@salonhub.test");
        long first = createCustomer(admin, "Ana", "44999990001", "ana@pages.test");
        createCustomer(admin, "Bea", "44999990002", "bea@pages.test");
        long last = createCustomer(admin, "Cris", "44999990003", "cris@pages.test");
        registerEstablishment("foreign.pages@salonhub.test", "11444777000161");
        createCustomer(login("foreign.pages@salonhub.test"), "Aaa", "44999990004", "foreign@pages.test");

        mvc.perform(get("/api/clientes/estabelecimento/me").header("Authorization", admin)
                .param("page", "0").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first));
        mvc.perform(get("/api/clientes/estabelecimento/me").header("Authorization", admin)
                .param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(last));
        mvc.perform(get("/api/clientes/estabelecimento/me").header("Authorization", admin)
                .param("search", "cris@pages.test"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(last));
    }

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
        mvc.perform(get("/api/clientes/estabelecimento/me").header("Authorization", admin)
                .param("search", "(44) 99999-0000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(existing));
        mvc.perform(get("/api/clientes/estabelecimento/me").header("Authorization", admin)
                .param("search", "44999990000"))
                .andExpect(jsonPath("$.content[0].id").value(existing));
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

    @Test
    void mergeRejectsOverlappingAppointmentsBeforeMovingThem() throws Exception {
        registerEstablishment("admin.merge.conflict@salonhub.test", "66777888000181");
        String admin = login("admin.merge.conflict@salonhub.test");
        long a = createCustomer(admin, "Cliente A", "44999990000", null);
        long b = createCustomer(admin, "Cliente B", "44999990000", null);
        long first = createProfessionalForMerge(admin, "prof.merge.a@salonhub.test");
        long second = createProfessionalForMerge(admin, "prof.merge.b@salonhub.test");
        long service = ((Number) JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Serviço merge\",\"price\":50,\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        LocalDate day = LocalDate.now().plusDays(2);
        while (day.getDayOfWeek().getValue() > 5) day = day.plusDays(1);
        long firstId = bookForMerge(admin, a, service, first, day + "T10:00:00");
        long secondId = bookForMerge(admin, b, service, second, day + "T10:30:00");
        mvc.perform(patch("/api/clientes/estabelecimento/me/{id}/mesclar/{otherId}", a, b)
                .header("Authorization", admin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("agendamentos conflitantes")));
        org.junit.jupiter.api.Assertions.assertEquals(a, jdbc.queryForObject("select customer_id from appointments where id = ?", Long.class, firstId));
        org.junit.jupiter.api.Assertions.assertEquals(b, jdbc.queryForObject("select customer_id from appointments where id = ?", Long.class, secondId));
    }

    private long createProfessionalForMerge(String admin, String email) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(post("/api/funcionarios")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Profissional\",\"email\":\"%s\",\"occupation\":\"Cabeleireiro(a)\",\"cpf\":\"%s\",\"hireDate\":\"2025-01-02\"}"
                        .formatted(email, TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long bookForMerge(String admin, long customer, long service, long professional, String startsAt) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customer)
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%s\"}"
                        .formatted(service, professional, startsAt)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
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
                        .formatted(firstAccessToken(employee)))).andExpect(status().isNoContent());
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
                .header("Authorization", admin)).andExpect(jsonPath("$.totalElements").value(1));

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
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].customerId").value(onlineId));
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
                .andExpect(jsonPath("$.totalElements").value(3));
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

    @Test
    void linkedCustomersShareConflictsAcrossEstablishmentsAndCounterCustomersKeepCustomerConflicts() throws Exception {
        registerClient("shared.conflict@salonhub.test", "44999994444");
        var client = users.findByEmailIgnoreCase("shared.conflict@salonhub.test").orElseThrow();

        registerEstablishment("admin.conflict.a@salonhub.test", "66777888000181");
        String adminA = login("admin.conflict.a@salonhub.test");
        long customerA = createCustomer(adminA, "Cliente vinculado A", "44999995555", null);
        linkCustomer(customerA, client.getId());
        long professionalA = createProfessionalForMerge(adminA, "professional.conflict.a@salonhub.test");
        long secondProfessionalA = createProfessionalForMerge(adminA, "professional.conflict.a2@salonhub.test");
        long thirdProfessionalA = createProfessionalForMerge(adminA, "professional.conflict.a3@salonhub.test");
        long serviceA = createServiceForConflict(adminA, "Servico conflito A");

        registerEstablishment("admin.conflict.b@salonhub.test", "11444777000161");
        String adminB = login("admin.conflict.b@salonhub.test");
        long customerB = createCustomer(adminB, "Cliente vinculado B", "44999996666", null);
        linkCustomer(customerB, client.getId());
        long professionalB = createProfessionalForMerge(adminB, "professional.conflict.b@salonhub.test");
        long serviceB = createServiceForConflict(adminB, "Servico conflito B");

        LocalDate day = LocalDate.now().plusDays(2);
        while (day.getDayOfWeek().getValue() > 5) day = day.plusDays(1);
        long originalAppointment = bookForMerge(adminA, customerA, serviceA, professionalA, day + "T10:00:00");

        bookForMergeExpectConflict(adminA, customerA, serviceA, secondProfessionalA, day + "T10:00:00");
        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customerB)
                        .header("Authorization", adminB).param("serviceId", String.valueOf(serviceB))
                        .param("professionalId", String.valueOf(professionalB)).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '10:00:00')]").isEmpty());
        bookForMergeExpectConflict(adminB, customerB, serviceB, professionalB, day + "T10:00:00");
        long nonOverlappingAppointment = bookForMerge(adminB, customerB, serviceB, professionalB, day + "T12:00:00");

        mvc.perform(put("/api/agendamentos/{id}/remarcacao", nonOverlappingAppointment)
                        .header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "professionalId", professionalB,
                                "startsAt", day + "T10:00:00"))))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", originalAppointment)
                        .header("Authorization", adminA).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Cliente desistiu"))))
                .andExpect(status().isOk());
        bookForMerge(adminB, customerB, serviceB, professionalB, day + "T10:00:00");

        long counterCustomer = createCustomer(adminA, "Cliente de balcao", "44999997777", null);
        bookForMerge(adminA, counterCustomer, serviceA, secondProfessionalA, day + "T10:00:00");
        bookForMergeExpectConflict(adminA, counterCustomer, serviceA, thirdProfessionalA, day + "T10:00:00");
    }

    private void linkCustomer(long customerId, long userId) {
        Customer customer = customers.findById(customerId).orElseThrow();
        customer.link(users.findById(userId).orElseThrow());
        customers.saveAndFlush(customer);
    }

    private long createServiceForConflict(String admin, String name) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", name,
                                "price", 50,
                                "durationMinutes", 60))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void bookForMergeExpectConflict(String admin, long customer, long service, long professional,
                                             String startsAt) throws Exception {
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customer)
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "serviceId", service,
                                "professionalId", professional,
                                "startsAt", startsAt))))
                .andExpect(status().isConflict());
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
        String firstAccessToken = firstAccessToken(employeeBody);
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

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
        mvc.perform(put("/api/clientes/estabelecimento/me/{id}", customerId.longValue())
                .header("Authorization", professionalAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maria do balcão\",\"phone\":\"44999990000\",\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/estabelecimento/me/{id}", customerId.longValue())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maria do balcão\",\"phone\":\"44999990000\",\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/clientes/estabelecimento/me/{id}", customerId.longValue())
                .header("Authorization", professionalAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maria do balcão\",\"phone\":\"44999990000\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/estabelecimento/me/{id}", customerId.longValue())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maria do balcão\",\"phone\":\"44999990000\",\"active\":true}"))
                .andExpect(status().isOk());
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
        mvc.perform(get("/api/clientes/estabelecimento/me/opcoes-agendamento").header("Authorization", auth)
                .param("serviceId", serviceId.toString()))
                .andExpect(jsonPath("$.professionals.length()").value(2));
        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customerId.longValue())
                .header("Authorization", professionalAuth).param("serviceId", serviceId.toString())
                .param("professionalId", colleagueId.toString()).param("date", day.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customerId.longValue())
                .header("Authorization", professionalAuth).param("serviceId", serviceId.toString())
                .param("professionalId", professionalId.toString()).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '10:00:00')]").isEmpty())
                .andExpect(jsonPath("$.availableTimes[?(@ == '11:15:00')]").isNotEmpty())
                .andExpect(jsonPath("$.availableTimes[?(@ == '17:30:00')]").isEmpty());
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
        customers.findById(customerId.longValue()).orElseThrow().getEstablishment()
                .updateAppointmentAdvancePolicy(3 * 24 * 60, 60);
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customerId.longValue())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT11:07:00\"}"
                        .formatted(serviceId.longValue(), professionalId.longValue(), day)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customerId.longValue())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT11:15:00\"}"
                        .formatted(serviceId.longValue(), professionalId.longValue(), day)))
                .andExpect(status().isCreated());
        registerClient("online.slot@salonhub.test", "44977771111");
        String onlineAuth = login("online.slot@salonhub.test");
        mvc.perform(post("/api/estabelecimentos/{id}/agendamentos",
                        customers.findById(customerId.longValue()).orElseThrow().getEstablishment().getId())
                .header("Authorization", onlineAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT12:15:00\"}"
                        .formatted(serviceId.longValue(), professionalId.longValue(), day)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("O horário deve estar alinhado aos intervalos de 30 minutos."));
        mvc.perform(post("/api/estabelecimentos/{id}/agendamentos",
                        customers.findById(customerId.longValue()).orElseThrow().getEstablishment().getId())
                .header("Authorization", onlineAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT12:30:00\"}"
                        .formatted(serviceId.longValue(), professionalId.longValue(), day)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customerId.longValue())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%sT15:00:00\"}"
                        .formatted(serviceId.longValue(), colleagueId.longValue(), day)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/profissionais/me/agenda/eventos").header("Authorization", professionalAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Bloqueio\",\"startsAt\":\"%sT12:30:00\",\"endsAt\":\"%sT13:00:00\",\"allDay\":false}"
                        .formatted(day, day))).andExpect(status().isCreated());
        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customerId.longValue())
                .header("Authorization", auth).param("serviceId", serviceId.toString())
                .param("professionalId", professionalId.toString()).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '12:30:00')]").isEmpty())
                .andExpect(jsonPath("$.availableTimes[?(@ == '15:15:00')]").isEmpty());
        mvc.perform(put("/api/funcionarios/{id}/servicos", colleagueId.longValue())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"allServices\":false,\"serviceIds\":[]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clientes/estabelecimento/me/opcoes-agendamento").header("Authorization", auth)
                .param("serviceId", serviceId.toString()))
                .andExpect(jsonPath("$.professionals.length()").value(1))
                .andExpect(jsonPath("$.professionals[0].id").value(professionalId));
        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customerId.longValue())
                .header("Authorization", auth).param("serviceId", serviceId.toString())
                .param("professionalId", colleagueId.toString()).param("date", day.toString()))
                .andExpect(status().isConflict());

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
