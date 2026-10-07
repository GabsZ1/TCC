package br.com.salonhub.controller;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.notification.MailDispatcher;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemEstablishmentControllerIntegrationTests {

    private static final String ENDPOINT = "/api/admin-sistema/estabelecimentos";

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;
    @MockitoBean private MailDispatcher dispatcher;

    @Test
    void systemAdministratorShouldControlAllStatesAndKeepCancellationsAfterReactivation() throws Exception {
        Establishment active = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Ativo para sistema").cnpj("12345678000195").build());
        Establishment inactive = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Inativo para sistema").cnpj("22345678000149").active(false).build());
        User systemAdministrator = userRepository.saveAndFlush(user(
                "Administrador Sistema", "system-admin@salonhub.test", UserRole.SYSTEM_ADMIN, null));
        User localAdministrator = userRepository.saveAndFlush(user(
                "Administrador Local", "state-local-admin@salonhub.test", UserRole.ADMIN, active));
        User professional = userRepository.saveAndFlush(user(
                "Profissional", "state-professional@salonhub.test", UserRole.PROFESSIONAL, active));
        User client = userRepository.saveAndFlush(user(
                "Cliente", "state-client@salonhub.test", UserRole.CLIENT, null));
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(active).name("Corte sistema").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(30).active(true).build());
        Appointment appointment = appointmentRepository.saveAndFlush(schedule(
                active, client, professional, service, LocalDateTime.now().plusDays(1)));

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(active.getId()))
                        .value(contains(true)))
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(inactive.getId()))
                        .value(contains(false)));

        mockMvc.perform(patch(ENDPOINT + "/{id}/bloqueio-agendamentos", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());
        assertThat(active.isBookingsBlocked()).isTrue();
        assertThat(active.isBookingsBlockedBySystem()).isTrue();
        mockMvc.perform(delete("/api/estabelecimentos/me/bloqueio-agendamentos")
                        .header("Authorization", bearer(localAdministrator)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(ENDPOINT + "/{id}/bloqueio-agendamentos", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch(ENDPOINT + "/{id}/pausa", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(ENDPOINT + "/{id}", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());

        assertThat(establishmentRepository.findById(active.getId()).orElseThrow().isActive()).isFalse();
        assertThat(establishmentRepository.findById(inactive.getId()).orElseThrow().isActive()).isFalse();
        Appointment cancelled = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Site desativado");
        assertThat(cancelled.getCancelledBy().getId()).isEqualTo(systemAdministrator.getId());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(localAdministrator)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch(ENDPOINT + "/{id}/ativacao", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());
        assertThat(active.isActive()).isTrue();
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);

        mockMvc.perform(patch(ENDPOINT + "/{id}/pausa", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());
        assertThat(active.isPausedBySystem()).isTrue();
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(professional)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(localAdministrator)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/estabelecimentos/me/pausa")
                        .header("Authorization", bearer(localAdministrator)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(ENDPOINT + "/{id}/pausa", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());
        assertThat(active.isPaused()).isFalse();
    }

    @Test
    void systemAdministratorShouldSeeEstablishmentDetails() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Detalhes para sistema").cnpj("52345678000100").email("contato@detalhes.test").build());
        User systemAdministrator = userRepository.saveAndFlush(user(
                "Administrador Sistema", "details-system-admin@salonhub.test", UserRole.SYSTEM_ADMIN, null));
        userRepository.saveAndFlush(user("Dona do salão", "details-owner@salonhub.test", UserRole.ADMIN, establishment));
        User professional = userRepository.saveAndFlush(user(
                "Profissional ativo", "details-professional@salonhub.test", UserRole.PROFESSIONAL, establishment));
        User inactiveProfessional = user("Profissional inativo", "details-inactive@salonhub.test", UserRole.PROFESSIONAL, establishment);
        inactiveProfessional.deactivate();
        userRepository.saveAndFlush(inactiveProfessional);
        User client = userRepository.saveAndFlush(user("Cliente", "details-client@salonhub.test", UserRole.CLIENT, null));
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte detalhes").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.TEN).durationMinutes(30).active(true).build());
        appointmentRepository.saveAndFlush(schedule(
                establishment, client, professional, service, LocalDateTime.now().plusDays(2)));

        mockMvc.perform(get(ENDPOINT + "/{id}", establishment.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishment.name").value("Detalhes para sistema"))
                .andExpect(jsonPath("$.administrators[0].email").value("details-owner@salonhub.test"))
                .andExpect(jsonPath("$.professionals.length()").value(2))
                .andExpect(jsonPath("$.professionals[0].name").value("Profissional ativo"))
                .andExpect(jsonPath("$.services[0].name").value("Corte detalhes"))
                .andExpect(jsonPath("$.stats.activeProfessionals").value(1))
                .andExpect(jsonPath("$.stats.totalProfessionals").value(2))
                .andExpect(jsonPath("$.stats.upcomingAppointments").value(1))
                .andExpect(jsonPath("$.stats.totalAppointments").value(1))
                .andExpect(jsonPath("$.profile").doesNotExist());

        mockMvc.perform(get(ENDPOINT + "/{id}", 999_999_999L).header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNotFound());
        User owner = userRepository.findByEmailIgnoreCase("details-owner@salonhub.test").orElseThrow();
        mockMvc.perform(get(ENDPOINT + "/{id}", establishment.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void establishmentAdministratorMustNotAccessSystemAdministration() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Sem acesso ao sistema").cnpj("32345678000102").build());
        User administrator = userRepository.saveAndFlush(user(
                "Administrador Local", "local-admin@salonhub.test", UserRole.ADMIN, establishment));

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearer(administrator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ENDPOINT + "/{id}", establishment.getId())
                        .header("Authorization", bearer(administrator)))
                .andExpect(status().isForbidden());

        assertThat(establishmentRepository.findById(establishment.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void deactivationNotifiesEachAffectedCustomerAndProfessionalOnce() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Avisos na desativação").cnpj("82345678000195").build());
        User systemAdministrator = userRepository.saveAndFlush(user(
                "Administrador Sistema", "deactivation-system@salonhub.test", UserRole.SYSTEM_ADMIN, null));
        User professional = userRepository.saveAndFlush(user(
                "Profissional", "deactivation-professional@salonhub.test", UserRole.PROFESSIONAL, establishment));
        User client = userRepository.saveAndFlush(user(
                "Cliente", "deactivation-client@salonhub.test", UserRole.CLIENT, null));
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(30).active(true).build());
        Appointment first = appointmentRepository.saveAndFlush(schedule(
                establishment, client, professional, service, LocalDateTime.now().plusDays(1).withHour(10)));
        Appointment second = appointmentRepository.saveAndFlush(schedule(
                establishment, client, professional, service, LocalDateTime.now().plusDays(1).withHour(11)));
        Appointment alreadyCancelled = schedule(
                establishment, client, professional, service, LocalDateTime.now().plusDays(1).withHour(12));
        alreadyCancelled.cancelByAdministrator("Cancelado antes", systemAdministrator, LocalDateTime.now());
        appointmentRepository.saveAndFlush(alreadyCancelled);

        clearInvocations(dispatcher);
        mockMvc.perform(delete(ENDPOINT + "/{id}", establishment.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());

        org.mockito.ArgumentCaptor<String> recipients = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(dispatcher, times(4)).send(recipients.capture(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        assertThat(recipients.getAllValues()).containsExactlyInAnyOrder(
                client.getEmail(), client.getEmail(), professional.getEmail(), professional.getEmail());
        assertThat(appointmentRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointmentRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointmentRepository.findById(alreadyCancelled.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
    }
    private User user(String name, String email, UserRole role, Establishment establishment) {
        return User.builder().name(name).email(email).password("senha-de-teste")
                .role(role).establishment(establishment).active(true).build();
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.generate(user).value();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CustomerRepository customerRepository;

    private Appointment schedule(br.com.salonhub.entity.Establishment establishment,
            br.com.salonhub.entity.User client, br.com.salonhub.entity.User professional,
            br.com.salonhub.entity.EstablishmentService service, java.time.LocalDateTime startsAt) {
        Customer customer = customerRepository.findByEstablishmentIdAndUserId(establishment.getId(), client.getId())
                .orElseGet(() -> {
                    Customer created = Customer.create(establishment, client.getName(), client.getPhone(), client.getEmail());
                    created.link(client);
                    return customerRepository.saveAndFlush(created);
                });
        return Appointment.schedule(establishment, customer, professional, service, startsAt);
    }}
