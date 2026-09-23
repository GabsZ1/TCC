package br.com.salonhub.controller;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstablishmentProfileControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TokenService tokenService;

    @Test
    void shouldCreateUpdateAndExposePublicProfileWithCurrentCatalog() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio Perfil").cnpj("12345678000195").build());
        User admin = user("Admin Perfil", "admin-profile@salonhub.test", UserRole.ADMIN, establishment);
        User client = user("Cliente Perfil", "client-profile@salonhub.test", UserRole.CLIENT, null);
        user("Ana Profissional", "professional-profile@salonhub.test", UserRole.PROFESSIONAL, establishment);
        serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte").description("Corte personalizado")
                .price(new BigDecimal("75.00")).commissionPercentage(BigDecimal.ZERO)
                .durationMinutes(45).active(true).build());
        serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Serviço inativo")
                .price(new BigDecimal("50.00")).commissionPercentage(BigDecimal.ZERO)
                .durationMinutes(30).active(false).build());

        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil", establishment.getId())
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentName").value("Studio Perfil"))
                .andExpect(jsonPath("$.aboutUs").isEmpty())
                .andExpect(jsonPath("$.services[0].name").value("Corte"))
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.professionals[0].name").value("Ana Profissional"));

        mockMvc.perform(put("/api/estabelecimentos/me/perfil")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aboutUs":"  Um espaço para cuidar de você.  ",
                                  "instagram":"https://instagram.com/studio.perfil/",
                                  "whatsapp":"(44) 99999-9999",
                                  "phone":"(44) 3333-3333",
                                  "postalCode":"87000000",
                                  "street":" Avenida Brasil ",
                                  "streetNumber":"100",
                                  "addressComplement":"Sala 2",
                                  "neighborhood":"Centro",
                                  "city":"Maringá",
                                  "state":"pr"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutUs").value("Um espaço para cuidar de você."))
                .andExpect(jsonPath("$.instagram").value("studio.perfil"))
                .andExpect(jsonPath("$.whatsapp").value("44999999999"))
                .andExpect(jsonPath("$.state").value("PR"));

        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil", establishment.getId())
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postalCode").value("87000000"))
                .andExpect(jsonPath("$.street").value("Avenida Brasil"));
    }

    @Test
    void shouldRejectInvalidProfileAndNonAdministratorUpdate() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio Validação").cnpj("22345678000149").build());
        User admin = user("Admin Validação", "admin-validation@salonhub.test", UserRole.ADMIN, establishment);
        User client = user("Cliente Validação", "client-validation@salonhub.test", UserRole.CLIENT, null);

        mockMvc.perform(put("/api/estabelecimentos/me/perfil")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postalCode\":\"123\",\"state\":\"XX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.postalCode").value("O CEP deve conter 8 números"))
                .andExpect(jsonPath("$.fields.state").value("Informe uma UF válida"));

        mockMvc.perform(put("/api/estabelecimentos/me/perfil")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private User user(String name, String email, UserRole role, Establishment establishment) {
        return userRepository.saveAndFlush(User.builder().name(name).email(email)
                .password("senha-de-teste").role(role).establishment(establishment).active(true).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.generate(user).value();
    }
}
