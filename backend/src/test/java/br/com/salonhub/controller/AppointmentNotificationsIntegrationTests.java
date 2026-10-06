package br.com.salonhub.controller;

import br.com.salonhub.notification.EmailContent;
import br.com.salonhub.notification.MailDispatcher;
import br.com.salonhub.service.appointment.AppointmentReminderService;
import br.com.salonhub.support.TestDocuments;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-mails de agendamento: confirmação, remarcação, cancelamento e lembrete, para o cliente e para
 * o profissional. O envio real é substituído por um mock do {@link MailDispatcher}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentNotificationsIntegrationTests extends FirstAccessIntegrationSupport {

    private static final String PASSWORD = "senha123";
    private static final String ADMIN_EMAIL = "admin.avisos@salonhub.test";
    private static final String ANA_EMAIL = "ana.avisos@salonhub.test";
    private static final String BRUNO_EMAIL = "bruno.avisos@salonhub.test";
    private static final String COUNTER_CUSTOMER_EMAIL = "balcao.avisos@salonhub.test";
    private static final String ONLINE_CLIENT_EMAIL = "online.avisos@salonhub.test";

    @Autowired private MockMvc mvc;
    @Autowired private AppointmentReminderService reminderService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @PersistenceContext private EntityManager entityManager;
    @MockitoBean private MailDispatcher dispatcher;

    private String admin;
    private long establishmentId;
    private long ana;
    private long bruno;
    private long service;
    private LocalDate day;

    private record Sent(String to, String subject, EmailContent content) {
        String details() {
            return content.details().toString();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        establishmentId = ((Number) JsonPath.read(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"ownerName":"Admin avisos","ownerEmail":"%s","password":"%s",
                                 "establishmentName":"Salão Avisos","cnpj":"71727374000151"}
                                """.formatted(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.establishmentId")).longValue();
        admin = login(ADMIN_EMAIL);
        String anaBody = createEmployee("Ana Souza", ANA_EMAIL);
        ana = ((Number) JsonPath.read(anaBody, "$.id")).longValue();
        mvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\",\"passwordConfirmation\":\"%s\"}"
                                .formatted(firstAccessToken(anaBody), PASSWORD, PASSWORD)))
                .andExpect(status().isNoContent());
        bruno = ((Number) JsonPath.read(createEmployee("Bruno Lima", BRUNO_EMAIL), "$.id")).longValue();
        service = ((Number) JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte avisos\",\"price\":50.00,\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        day = LocalDate.now(clock).plusDays(3);
        while (day.getDayOfWeek().getValue() > 5) day = day.plusDays(1);
        clearInvocations(dispatcher);
    }

    @Test
    void counterBookingConfirmsToTheCustomerAndWarnsTheProfessional() throws Exception {
        long withEmail = createCustomer("Carla Mendes", "44999991111", COUNTER_CUSTOMER_EMAIL);
        long withoutEmail = createCustomer("Dora Sem Email", "44999992222", null);

        book(admin, withEmail, ana, day + "T10:00:00");
        List<Sent> sent = sent();
        assertThat(sent).extracting(Sent::to).containsExactlyInAnyOrder(COUNTER_CUSTOMER_EMAIL, ANA_EMAIL);
        Sent confirmation = to(sent, COUNTER_CUSTOMER_EMAIL);
        assertThat(confirmation.subject()).startsWith("Agendamento confirmado: Corte avisos em ");
        assertThat(confirmation.content().greeting()).isEqualTo("Olá, Carla!");
        assertThat(confirmation.details()).contains("Ana Souza", "Salão Avisos", "1 h");
        // Cliente de balcão não tem conta: sem botão, com orientação para falar com o salão.
        assertThat(confirmation.content().action()).isNull();
        assertThat(to(sent, ANA_EMAIL).subject()).startsWith("Novo agendamento: Carla Mendes em ");

        clearInvocations(dispatcher);
        book(admin, withoutEmail, ana, day + "T14:00:00");
        assertThat(sent()).extracting(Sent::to).containsExactly(ANA_EMAIL);
    }

    @Test
    void professionalBookingForHimselfDoesNotWarnHimself() throws Exception {
        long customer = createCustomer("Carla Mendes", "44999991111", COUNTER_CUSTOMER_EMAIL);

        book(login(ANA_EMAIL), customer, ana, day + "T10:00:00");

        assertThat(sent()).extracting(Sent::to).containsExactly(COUNTER_CUSTOMER_EMAIL);
    }

    @Test
    void onlineClientGetsALinkAndTheProfessionalLearnsAboutTheCancellation() throws Exception {
        mvc.perform(post("/api/auth/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Marina Online\",\"email\":\"%s\",\"phone\":\"44988887777\",\"password\":\"%s\",\"passwordConfirmation\":\"%s\"}"
                                .formatted(ONLINE_CLIENT_EMAIL, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        String client = login(ONLINE_CLIENT_EMAIL);
        long appointment = ((Number) JsonPath.read(mvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId)
                        .header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                        .content(body(service, ana, day + "T11:00:00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        Sent confirmation = to(sent(), ONLINE_CLIENT_EMAIL);
        assertThat(confirmation.content().action()).isNotNull();
        assertThat(confirmation.content().action().url()).endsWith("/agendamentos");

        clearInvocations(dispatcher);
        mvc.perform(patch("/api/agendamentos/{id}/cancelamento", appointment).header("Authorization", client))
                .andExpect(status().isOk());
        List<Sent> sent = sent();
        assertThat(sent).extracting(Sent::to).containsExactly(ANA_EMAIL);
        assertThat(sent.getFirst().subject()).startsWith("Agendamento cancelado: Marina Online em ");
    }

    @Test
    void administratorRescheduleWarnsTheCustomerAndBothProfessionals() throws Exception {
        long customer = createCustomer("Carla Mendes", "44999991111", COUNTER_CUSTOMER_EMAIL);
        long appointment = book(admin, customer, ana, day + "T10:00:00");
        clearInvocations(dispatcher);

        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(null, bruno, day + "T15:00:00")))
                .andExpect(status().isOk());

        List<Sent> sent = sent();
        assertThat(sent).extracting(Sent::to)
                .containsExactlyInAnyOrder(COUNTER_CUSTOMER_EMAIL, ANA_EMAIL, BRUNO_EMAIL);
        Sent customerEmail = to(sent, COUNTER_CUSTOMER_EMAIL);
        assertThat(customerEmail.subject()).startsWith("Agendamento remarcado: ");
        assertThat(customerEmail.details()).contains("Novo horário", "Horário anterior", "Bruno Lima");
        assertThat(to(sent, ANA_EMAIL).subject()).startsWith("Agendamento transferido: ");
        assertThat(to(sent, BRUNO_EMAIL).subject()).startsWith("Novo agendamento: ");
    }

    @Test
    void administratorCancellationSendsTheReasonToCustomerAndProfessional() throws Exception {
        long customer = createCustomer("Carla Mendes", "44999991111", COUNTER_CUSTOMER_EMAIL);
        long appointment = book(admin, customer, ana, day + "T10:00:00");
        clearInvocations(dispatcher);

        mvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", appointment)
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Profissional em curso de capacitação\"}"))
                .andExpect(status().isOk());

        List<Sent> sent = sent();
        assertThat(sent).extracting(Sent::to).containsExactlyInAnyOrder(COUNTER_CUSTOMER_EMAIL, ANA_EMAIL);
        assertThat(to(sent, COUNTER_CUSTOMER_EMAIL).subject()).startsWith("Agendamento cancelado: Corte avisos em ");
        assertThat(to(sent, COUNTER_CUSTOMER_EMAIL).details()).contains("Profissional em curso de capacitação");
        assertThat(to(sent, ANA_EMAIL).details()).contains("Profissional em curso de capacitação");
    }

    @Test
    void reminderIsSentOnceAndOnlyForAppointmentsBookedInAdvance() throws Exception {
        long remindedCustomer = createCustomer("Carla Mendes", "44999991111", COUNTER_CUSTOMER_EMAIL);
        long lateCustomer = createCustomer("Lia Última Hora", "44999993333", "lia.avisos@salonhub.test");
        long reminded = book(admin, remindedCustomer, ana, day + "T10:00:00");
        long lastMinute = book(admin, lateCustomer, bruno, day + "T10:00:00");

        // Traz os dois horários para daqui a 3 horas; só o primeiro foi marcado com antecedência.
        LocalDateTime soon = LocalDateTime.now(clock).plusHours(3).withSecond(0).withNano(0);
        entityManager.flush();
        moveTo(reminded, soon, OffsetDateTime.now(clock).minusDays(3));
        moveTo(lastMinute, soon, OffsetDateTime.now(clock).minusHours(1));
        entityManager.clear();
        clearInvocations(dispatcher);

        reminderService.sendDueReminders();
        List<Sent> sent = sent();
        assertThat(sent).filteredOn(email -> email.to().equals(COUNTER_CUSTOMER_EMAIL))
                .singleElement()
                .satisfies(email -> assertThat(email.subject()).startsWith("Lembrete: Corte avisos em "));
        assertThat(sent).noneMatch(email -> email.to().equals("lia.avisos@salonhub.test"));

        entityManager.flush();
        entityManager.clear();
        clearInvocations(dispatcher);
        reminderService.sendDueReminders();
        assertThat(sent()).noneMatch(email -> email.to().equals(COUNTER_CUSTOMER_EMAIL));
    }

    private void moveTo(long appointmentId, LocalDateTime startsAt, OffsetDateTime createdAt) {
        jdbc.update("update appointments set starts_at = ?, ends_at = ?, created_at = ? where id = ?",
                Timestamp.valueOf(startsAt), Timestamp.valueOf(startsAt.plusHours(1)), createdAt, appointmentId);
    }

    private List<Sent> sent() {
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EmailContent> content = ArgumentCaptor.forClass(EmailContent.class);
        verify(dispatcher, atLeast(0)).send(to.capture(), subject.capture(), content.capture());
        List<Sent> sent = new ArrayList<>();
        for (int index = 0; index < to.getAllValues().size(); index++) {
            sent.add(new Sent(to.getAllValues().get(index), subject.getAllValues().get(index),
                    content.getAllValues().get(index)));
        }
        return sent;
    }

    private static Sent to(List<Sent> sent, String recipient) {
        return sent.stream().filter(email -> email.to().equals(recipient)).findFirst()
                .orElseThrow(() -> new AssertionError("Nenhum e-mail para " + recipient + " em " + sent));
    }

    private String login(String email) throws Exception {
        return "Bearer " + JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String createEmployee(String name, String email) throws Exception {
        return mvc.perform(post("/api/funcionarios").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"occupation\":\"Cabeleireiro(a)\",\"cpf\":\"%s\",\"hireDate\":\"2025-01-02\"}"
                                .formatted(name, email, TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private long createCustomer(String name, String phone, String email) throws Exception {
        String emailJson = email == null ? "null" : "\"" + email + "\"";
        return ((Number) JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"phone\":\"%s\",\"email\":%s}".formatted(name, phone, emailJson)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long book(String auth, long customer, long professional, String startsAt) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customer)
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body(service, professional, startsAt)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String body(Long serviceId, long professionalId, String startsAt) {
        return "{\"serviceId\":%s,\"professionalId\":%d,\"startsAt\":\"%s\"}"
                .formatted(serviceId == null ? "null" : serviceId.toString(), professionalId, startsAt);
    }
}
