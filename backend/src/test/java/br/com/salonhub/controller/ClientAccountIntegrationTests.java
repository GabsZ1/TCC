package br.com.salonhub.controller;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 2.23 (cadastro com telefone e confirmação de senha), 2.24 (editar perfil),
 * 2.25 (ver perfil) e 2.26 (excluir conta).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientAccountIntegrationTests {

    private static final String PROFILE = "/api/clientes/me";
    private static final String PASSWORD = "senha123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    // ---------- US 2.23 — cadastro ----------

    @Test
    void shouldRegisterClientWithNormalizedPhoneAndLetThemLogin() throws Exception {
        String email = uniqueEmail("cadastro");

        register("Maria Cliente", email, "(44) 99999-1234", PASSWORD, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"));

        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getPhone()).isEqualTo("44999991234");
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldRejectRegistrationWhenPasswordsDoNotMatch() throws Exception {
        register("Maria", uniqueEmail("senhas"), "44999991234", PASSWORD, "outra-senha")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("As senhas não coincidem!"));
    }

    @Test
    void shouldRejectRegistrationWithInvalidPhoneOrEmailOrMissingFields() throws Exception {
        register("Maria", uniqueEmail("telefone"), "123", PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe um telefone válido!"));

        register("Maria", "email-invalido", "44999991234", PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("Informe um e-mail válido!"));

        mockMvc.perform(post("/api/auth/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria","email":"%s","password":"senha123"}
                                """.formatted(uniqueEmail("faltando"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").exists())
                .andExpect(jsonPath("$.fields.passwordConfirmation").exists());
    }

    // ---------- US 2.25 — ver perfil ----------

    @Test
    void shouldShowOnlyTheAuthenticatedClientProfileWithoutPassword() throws Exception {
        User client = createClient();

        perform(client, get(PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(client.getId()))
                .andExpect(jsonPath("$.name").value(client.getName()))
                .andExpect(jsonPath("$.email").value(client.getEmail()))
                .andExpect(jsonPath("$.phone").value("44988887777"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldBeAvailableOnlyToClients() throws Exception {
        Fixture fixture = fixture();

        perform(fixture.administrator(), get(PROFILE)).andExpect(status().isForbidden());
        perform(fixture.professional(), get(PROFILE)).andExpect(status().isForbidden());
        mockMvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PROFILE).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- US 2.24 — editar perfil ----------

    @Test
    void shouldUpdateProfileAndKeepAppointmentsLinked() throws Exception {
        Fixture fixture = fixture();
        appointment(fixture, LocalDateTime.now().plusDays(3));
        String newEmail = uniqueEmail("novo");

        perform(fixture.client(), put(PROFILE).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Nome Novo","email":"%s","phone":"(44) 3333-2222"}
                """.formatted(newEmail.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Novo"))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.phone").value("4433332222"));

        perform(fixture.client(), get("/api/agendamentos/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        login(newEmail, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidProfileChanges() throws Exception {
        User client = createClient();
        User other = createClient();

        perform(client, put(PROFILE).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Maria","email":"%s","phone":"44988887777"}
                """.formatted(other.getEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Este e-mail já está cadastrado!"));

        perform(client, put(PROFILE).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Maria","email":"sem-arroba.com","phone":"44988887777"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("Informe um e-mail válido!"));

        perform(client, put(PROFILE).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"  ","email":"%s","phone":"44988887777"}
                """.formatted(client.getEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());

        perform(client, put(PROFILE).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Maria","email":"%s","phone":"12"}
                """.formatted(client.getEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe um telefone válido!"));
    }

    // ---------- US 2.26 — excluir conta ----------

    @Test
    void shouldKeepTheAccountWhenThePasswordIsWrong() throws Exception {
        User client = createClient();

        perform(client, delete(PROFILE).contentType(MediaType.APPLICATION_JSON).content("""
                {"password":"senha-errada"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Senha incorreta. A conta não foi excluída."));

        assertThat(userRepository.findById(client.getId()).orElseThrow().isActive()).isTrue();
        login(client.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldCloseTheAccountKeepHistoryCancelFutureBookingsAndFreeTheEmail() throws Exception {
        Fixture fixture = fixture();
        String originalEmail = fixture.client().getEmail();
        Appointment past = appointment(fixture, LocalDateTime.now().minusDays(10));
        past.complete(LocalDateTime.now().minusDays(10).plusHours(1));
        appointmentRepository.saveAndFlush(past);
        Appointment future = appointment(fixture, LocalDateTime.now().plusDays(5));

        perform(fixture.client(), delete(PROFILE).contentType(MediaType.APPLICATION_JSON).content("""
                {"password":"senha123"}
                """))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        User closed = userRepository.findById(fixture.client().getId()).orElseThrow();
        assertThat(closed.isActive()).isFalse();
        assertThat(closed.getDeletedAt()).isNotNull();
        assertThat(closed.getName()).isEqualTo("Cliente removido");
        assertThat(closed.getEmail()).isNotEqualTo(originalEmail);
        assertThat(closed.getPhone()).isNull();

        Appointment keptPast = appointmentRepository.findById(past.getId()).orElseThrow();
        assertThat(keptPast.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(keptPast.getClient().getId()).isEqualTo(closed.getId());
        Appointment cancelledFuture = appointmentRepository.findById(future.getId()).orElseThrow();
        assertThat(cancelledFuture.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelledFuture.getCancellationReason()).isEqualTo("Conta do cliente excluída");

        login(originalEmail, PASSWORD).andExpect(status().isUnauthorized());
        perform(closed, get(PROFILE)).andExpect(status().isUnauthorized());
        register("Nova Conta", originalEmail, "44977776666", PASSWORD, PASSWORD).andExpect(status().isCreated());
    }

    // ---------- apoio ----------

    private ResultActions register(String name, String email, String phone, String password, String confirmation)
            throws Exception {
        return mockMvc.perform(post("/api/auth/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","email":"%s","phone":"%s","password":"%s","passwordConfirmation":"%s"}
                        """.formatted(name, email, phone, password, confirmation)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.getId().toString()))));
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@cliente.test";
    }

    private User createClient() {
        return userRepository.saveAndFlush(User.builder()
                .name("Cliente " + UUID.randomUUID().toString().substring(0, 6))
                .email(uniqueEmail("cliente"))
                .password(passwordEncoder.encode(PASSWORD))
                .phone("44988887777")
                .role(UserRole.CLIENT)
                .build());
    }

    private Appointment appointment(Fixture fixture, LocalDateTime startsAt) {
        return appointmentRepository.saveAndFlush(Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(), startsAt));
    }

    private Fixture fixture() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão cliente " + unique).cnpj(cnpj).build());
        User administrator = userRepository.saveAndFlush(User.builder()
                .name("Admin").email("admin-" + unique + "@cliente.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.ADMIN).establishment(establishment).build());
        User professional = userRepository.saveAndFlush(User.builder()
                .name("Profissional").email("prof-" + unique + "@cliente.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.PROFESSIONAL).establishment(establishment).build());
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte").price(new BigDecimal("80.00"))
                .commissionPercentage(new BigDecimal("10.00")).durationMinutes(60).build());
        return new Fixture(establishment, administrator, professional, createClient(), service);
    }

    private record Fixture(
            Establishment establishment,
            User administrator,
            User professional,
            User client,
            EstablishmentService service) {
    }
}
