package br.com.salonhub.controller;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o ciclo de vida do atendimento depois do agendamento confirmado:
 * conclusão (CONFIRMED -> COMPLETED) e invalidação (COMPLETED -> INVALIDATED).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentCompletionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EstablishmentServiceRepository serviceRepository;

    @Test
    void shouldAllowOwningProfessionalToCompletePastConfirmedAppointment() throws Exception {
        Fixture fixture = setUp("complete-owner", "12345678000195");
        Long appointmentId = createPastAppointment(fixture);

        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldAllowAdministratorToCompleteAppointmentOnBehalfOfProfessional() throws Exception {
        Fixture fixture = setUp("complete-admin", "22345678000149");
        Long appointmentId = createPastAppointment(fixture);

        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldForbidClientFromCompletingAppointment() throws Exception {
        Fixture fixture = setUp("complete-client-forbidden", "32345678000100");
        Long appointmentId = createPastAppointment(fixture);

        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(fixture.clientToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldForbidAnotherProfessionalFromCompletingSomeoneElsesAppointment() throws Exception {
        Fixture fixture = setUp("complete-other-professional", "42345678000156");
        Long appointmentId = createPastAppointment(fixture);

        MvcResult otherProfessional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Outro Profissional","email":"other.professional.complete-other-professional@salonhub.test"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String otherFirstAccessToken = JsonPath.read(
                otherProfessional.getResponse().getContentAsString(), "$.firstAccessToken");
        completeFirstAccess(otherFirstAccessToken, "senha123");
        String otherProfessionalToken = login("other.professional.complete-other-professional@salonhub.test");

        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(otherProfessionalToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCompletionBeforeAppointmentStartTime() throws Exception {
        Fixture fixture = setUp("complete-future", "52345678000100");
        Appointment appointment = appointmentRepository.saveAndFlush(Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(),
                LocalDateTime.now().plusDays(2)));

        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointment.getId())
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("O agendamento só pode ser concluído a partir do horário previsto de início."));
    }

    @Test
    void shouldRejectCompletingAnAlreadyCompletedAppointment() throws Exception {
        Fixture fixture = setUp("complete-twice", "62345678000163");
        Long appointmentId = createPastAppointment(fixture);

        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Apenas agendamentos confirmados podem ser concluídos."));
    }

    @Test
    void shouldAllowAdministratorToInvalidateCompletedAppointmentWithReason() throws Exception {
        Fixture fixture = setUp("invalidate-ok", "72345678000117");
        Long appointmentId = createPastAppointment(fixture);
        completeAsProfessional(fixture, appointmentId);

        mockMvc.perform(patch("/api/agendamentos/{id}/invalidacao", appointmentId)
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Cliente não compareceu, marcação incorreta do profissional."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALIDATED"))
                .andExpect(jsonPath("$.invalidationReason")
                        .value("Cliente não compareceu, marcação incorreta do profissional."));
    }

    @Test
    void shouldRejectInvalidationWithBlankReason() throws Exception {
        Fixture fixture = setUp("invalidate-blank-reason", "82345678000170");
        Long appointmentId = createPastAppointment(fixture);
        completeAsProfessional(fixture, appointmentId);

        mockMvc.perform(patch("/api/agendamentos/{id}/invalidacao", appointmentId)
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidatingAnAppointmentThatIsNotCompleted() throws Exception {
        Fixture fixture = setUp("invalidate-not-completed", "92345678000124");
        Long appointmentId = createPastAppointment(fixture);

        mockMvc.perform(patch("/api/agendamentos/{id}/invalidacao", appointmentId)
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Tentativa indevida"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Apenas agendamentos concluídos podem ser invalidados."));
    }

    @Test
    void shouldForbidProfessionalFromInvalidatingAppointment() throws Exception {
        Fixture fixture = setUp("invalidate-professional-forbidden", "13456789000131");
        Long appointmentId = createPastAppointment(fixture);
        completeAsProfessional(fixture, appointmentId);

        mockMvc.perform(patch("/api/agendamentos/{id}/invalidacao", appointmentId)
                        .header("Authorization", bearer(fixture.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Tentativa indevida"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldNotAllowAdministratorFromAnotherEstablishmentToInvalidateAppointment() throws Exception {
        Fixture fixture = setUp("invalidate-tenant-isolation", "23456789000195");
        Long appointmentId = createPastAppointment(fixture);
        completeAsProfessional(fixture, appointmentId);

        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Outro Administrador",
                                  "ownerEmail": "other.admin.invalidate-tenant-isolation@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Outro Salão",
                                  "cnpj": "33456789000149"
                                }
                                """))
                .andExpect(status().isCreated());
        String otherAdminToken = login("other.admin.invalidate-tenant-isolation@salonhub.test");

        mockMvc.perform(patch("/api/agendamentos/{id}/invalidacao", appointmentId)
                        .header("Authorization", bearer(otherAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Tentativa indevida entre estabelecimentos"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowAdministratorToCancelConfirmedAppointmentAndPersistAudit() throws Exception {
        Fixture fixture = setUp("admin-cancel", "12345678000195");
        Appointment appointment = appointmentRepository.saveAndFlush(Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(),
                LocalDateTime.now().plusDays(2)));

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", appointment.getId())
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Profissional indisponível para o atendimento."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason")
                        .value("Profissional indisponível para o atendimento."))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.cancelledByUserId").isNumber())
                .andExpect(jsonPath("$.cancelledByUserName").value("Administrador admin-cancel"));

        appointmentRepository.flush();
        Appointment cancelled = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(cancelled.getCancellationReason()).isEqualTo("Profissional indisponível para o atendimento.");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancelledBy().getEmail()).isEqualTo("admin.admin-cancel@salonhub.test");
        assertThat(cancelled.getServiceName()).isEqualTo(appointment.getServiceName());
        assertThat(cancelled.getAgreedPrice()).isEqualByComparingTo(appointment.getAgreedPrice());
        assertThat(cancelled.getCommissionAmount()).isEqualByComparingTo(appointment.getCommissionAmount());
    }

    @Test
    void shouldRejectAdministrativeCancellationWithoutReason() throws Exception {
        Fixture fixture = setUp("admin-cancel-blank", "12345678000195");
        Appointment appointment = appointmentRepository.saveAndFlush(Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(),
                LocalDateTime.now().plusDays(2)));

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", appointment.getId())
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepCompletedAppointmentForInvalidationInsteadOfAdministrativeCancellation() throws Exception {
        Fixture fixture = setUp("admin-cancel-completed", "12345678000195");
        Long appointmentId = createPastAppointment(fixture);
        completeAsProfessional(fixture, appointmentId);

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", appointmentId)
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Tentativa sobre atendimento concluído."}
                                """))
                .andExpect(status().isConflict());

        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo(br.com.salonhub.entity.AppointmentStatus.COMPLETED);
    }
    @Test
    void shouldHideAppointmentFromAdministratorOfAnotherEstablishmentOnCancellation() throws Exception {
        Fixture fixture = setUp("admin-cancel-isolation", "12345678000195");
        Appointment appointment = appointmentRepository.saveAndFlush(Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(),
                LocalDateTime.now().plusDays(2)));
        setUp("other-admin-cancel", "22345678000149");
        String otherAdministratorToken = login("admin.other-admin-cancel@salonhub.test");

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", appointment.getId())
                        .header("Authorization", bearer(otherAdministratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Tentativa entre estabelecimentos."}
                                """))
                .andExpect(status().isNotFound());

        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(br.com.salonhub.entity.AppointmentStatus.CONFIRMED);
    }
    private void completeAsProfessional(Fixture fixture, Long appointmentId) throws Exception {
        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isOk());
    }

    private Long createPastAppointment(Fixture fixture) {
        Appointment appointment = Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(),
                LocalDateTime.now().minusDays(1));
        return appointmentRepository.saveAndFlush(appointment).getId();
    }

    private Fixture setUp(String suffix, String cnpj) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador %s",
                                  "ownerEmail": "admin.%s@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Salão %s",
                                  "cnpj": "%s"
                                }
                                """.formatted(suffix, suffix, suffix, cnpj)))
                .andExpect(status().isCreated())
                .andReturn();
        Number establishmentId = JsonPath.read(
                registration.getResponse().getContentAsString(), "$.establishmentId");
        String administratorToken = login("admin.%s@salonhub.test".formatted(suffix));

        MvcResult professional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Profissional %s","email":"professional.%s@salonhub.test"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstAccessToken = JsonPath.read(
                professional.getResponse().getContentAsString(), "$.firstAccessToken");
        completeFirstAccess(firstAccessToken, "senha123");
        String professionalToken = login("professional.%s@salonhub.test".formatted(suffix));

        mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Corte %s","price":50.00,"durationMinutes":60}
                                """.formatted(suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"client.%s@salonhub.test","password":"senha123"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());
        String clientToken = login("client.%s@salonhub.test".formatted(suffix));

        Establishment establishment = establishmentRepository.findById(establishmentId.longValue()).orElseThrow();
        User professionalUser = userRepository.findByEmailIgnoreCase("professional.%s@salonhub.test".formatted(suffix))
                .filter(user -> user.getRole() == UserRole.PROFESSIONAL)
                .orElseThrow();
        User clientUser = userRepository.findByEmailIgnoreCase("client.%s@salonhub.test".formatted(suffix))
                .orElseThrow();
        EstablishmentService establishmentService = serviceRepository
                .findByEstablishmentId(establishmentId.longValue()).stream()
                .findFirst()
                .orElseThrow();

        return new Fixture(
                administratorToken, professionalToken, clientToken,
                establishment, professionalUser, clientUser, establishmentService);
    }

    private record Fixture(
            String administratorToken,
            String professionalToken,
            String clientToken,
            Establishment establishment,
            User professional,
            User client,
            EstablishmentService service) {
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
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
