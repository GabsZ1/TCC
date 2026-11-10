package br.com.salonhub.controller;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.EstablishmentProfileRepository;
import br.com.salonhub.repository.EstablishmentGalleryPhotoRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.auth.TokenService;
import br.com.salonhub.service.geocoding.Coordinates;
import br.com.salonhub.service.geocoding.GeocodingService;
import br.com.salonhub.service.geocoding.BrasilApiCepGeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica a atualização e a consulta do perfil público. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.storage.type=memory")
class EstablishmentProfileControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private EstablishmentProfileRepository profileRepository;
    @Autowired private EstablishmentGalleryPhotoRepository galleryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TokenService tokenService;
    @MockitoBean private GeocodingService geocodingService;
    @MockitoBean private BrasilApiCepGeocodingService brasilApiCepGeocodingService;

    @BeforeEach
    void stubGeocoding() {
        when(geocodingService.geocode(anyString())).thenReturn(Optional.empty());
        when(brasilApiCepGeocodingService.geocodePostalCode(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void onlyOwnAdministratorCanManageLogoAndCover() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio Mídia").cnpj("12345678000195").build());
        User admin = user("Admin Mídia", "admin-media@salonhub.test", UserRole.ADMIN, establishment);
        User client = user("Cliente Mídia", "client-media@salonhub.test", UserRole.CLIENT, null);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        mockMvc.perform(multipart("/api/estabelecimentos/me/perfil/imagens/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", png))
                .header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil", establishment.getId())
                .header("Authorization", bearer(client)))
                .andExpect(jsonPath("$.hasLogo").value(true)).andExpect(jsonPath("$.hasCover").value(false));
        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil/imagens/logo", establishment.getId())
                .header("Authorization", bearer(client))).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/estabelecimentos/me/perfil/imagens/capa")
                .file(new MockMultipartFile("file", "cover.png", "image/png", png))
                .header("Authorization", bearer(client))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/estabelecimentos/me/perfil/imagens/logo")
                .header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil/imagens/logo", establishment.getId())
                .header("Authorization", bearer(client))).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateUpdateAndExposePublicProfileWithCurrentCatalog() throws Exception {
        when(geocodingService.geocode(anyString())).thenReturn(Optional.of(
                new Coordinates(new BigDecimal("-23.420999"), new BigDecimal("-51.933056"))));
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
                .andExpect(jsonPath("$.state").value("PR"))
                .andExpect(jsonPath("$.latitude").value(-23.420999))
                .andExpect(jsonPath("$.longitude").value(-51.933056));
        verify(geocodingService).geocode("Avenida Brasil, 100, Centro, Maringá, PR, 87000000, Brasil");
        var savedLocation = profileRepository.findByEstablishmentId(establishment.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(savedLocation.getLatitude()).isEqualByComparingTo("-23.420999");
        org.assertj.core.api.Assertions.assertThat(savedLocation.getLongitude()).isEqualByComparingTo("-51.933056");

        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil", establishment.getId())
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postalCode").value("87000000"))
                .andExpect(jsonPath("$.street").value("Avenida Brasil"))
                .andExpect(jsonPath("$.locationAvailable").value(true));
    }

    @Test
    void galleryEnforcesLimitOrderAndTenantAccess() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio Galeria").cnpj("62345678000113").build());
        Establishment other = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Outro Studio").cnpj("72345678000104").build());
        User admin = user("Admin Galeria", "admin-gallery@salonhub.test", UserRole.ADMIN, establishment);
        User otherAdmin = user("Outro Admin", "other-admin-gallery@salonhub.test", UserRole.ADMIN, other);
        User client = user("Cliente Galeria", "client-gallery@salonhub.test", UserRole.CLIENT, null);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        for (int index = 0; index < 15; index++) {
            mockMvc.perform(multipart("/api/estabelecimentos/me/perfil/galeria")
                    .file(new MockMultipartFile("file", "ambiente.png", "image/png", png))
                    .header("Authorization", bearer(admin))).andExpect(status().isOk());
        }
        mockMvc.perform(multipart("/api/estabelecimentos/me/perfil/galeria")
                .file(new MockMultipartFile("file", "extra.png", "image/png", png))
                .header("Authorization", bearer(admin))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil/galeria", establishment.getId())
                .header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(15));

        var saved = galleryRepository.findByEstablishmentIdOrderByDisplayOrderAscIdAsc(establishment.getId());
        Long first = saved.get(0).getId();
        Long last = saved.get(14).getId();
        String reversed = saved.reversed().stream().map(photo -> photo.getId().toString())
                .collect(java.util.stream.Collectors.joining(",", "{\"photoIds\":[", "]}"));
        mockMvc.perform(put("/api/estabelecimentos/me/perfil/galeria/ordem")
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(reversed))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(last));
        mockMvc.perform(get("/api/estabelecimentos/{id}/perfil/galeria/{photoId}", establishment.getId(), first)
                .header("Authorization", bearer(client))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/estabelecimentos/me/perfil/galeria/{photoId}", first)
                .header("Authorization", bearer(otherAdmin))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/estabelecimentos/me/perfil/galeria/{photoId}", first)
                .header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        mockMvc.perform(multipart("/api/estabelecimentos/me/perfil/galeria")
                .file(new MockMultipartFile("file", "nova.png", "image/png", png))
                .header("Authorization", bearer(admin))).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/estabelecimentos/me/perfil/galeria")
                .file(new MockMultipartFile("file", "cliente.png", "image/png", png))
                .header("Authorization", bearer(client))).andExpect(status().isForbidden());
    }

    @Test
    void proximityUsesStoredCoordinatesAndPlacesUnknownLocationsLast() throws Exception {
        Establishment close = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio Próximo").cnpj("32345678000140").build());
        Establishment unknown = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio Sem Ponto").cnpj("42345678000131").build());
        User admin = user("Admin Próximo", "admin-nearby@salonhub.test", UserRole.ADMIN, close);
        User client = user("Cliente Próximo", "client-nearby@salonhub.test", UserRole.CLIENT, null);
        // Ponto que o seed não usa: o banco local pode ter estabelecimentos de demonstração
        // em Maringá, e eles empatariam em distância zero com o estabelecimento do teste.
        when(geocodingService.geocode(anyString())).thenReturn(Optional.of(
                new Coordinates(new BigDecimal("-9.876543"), new BigDecimal("-35.123456"))));

        mockMvc.perform(put("/api/estabelecimentos/me/perfil")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"Avenida Brasil\",\"streetNumber\":\"100\",\"city\":\"Maringá\",\"state\":\"PR\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/estabelecimentos/proximos")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-9.876543,\"longitude\":-35.123456}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(close.getId()))
                .andExpect(jsonPath("$[0].distanceKm").value(0.0))
                .andReturn().getResponse().getContentAsString();

        // Outros estabelecimentos do banco podem aparecer na lista; o que importa é que os
        // sem localização fiquem todos no fim, e o do teste entre eles.
        List<Long> ids = JsonPath.<List<Number>>read(body, "$[*].id").stream()
                .map(Number::longValue).toList();
        List<Object> distances = JsonPath.read(body, "$[*].distanceKm");
        int unknownIndex = ids.indexOf(unknown.getId());
        org.assertj.core.api.Assertions.assertThat(unknownIndex).isPositive();
        org.assertj.core.api.Assertions.assertThat(distances.subList(unknownIndex, distances.size()))
                .containsOnlyNulls();
    }

    @Test
    void fallsBackToPostalCodeCoordinatesWithoutGeoapifyKey() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio CEP").cnpj("52345678000122").build());
        User admin = user("Admin CEP", "admin-cep@salonhub.test", UserRole.ADMIN, establishment);
        when(brasilApiCepGeocodingService.geocodePostalCode("87000000")).thenReturn(Optional.of(
                new Coordinates(new BigDecimal("-23.420999"), new BigDecimal("-51.933056"))));

        mockMvc.perform(put("/api/estabelecimentos/me/perfil")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postalCode\":\"87000000\",\"street\":\"Avenida Brasil\",\"streetNumber\":\"100\",\"city\":\"Maringá\",\"state\":\"PR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationAvailable").value(true))
                .andExpect(jsonPath("$.latitude").value(-23.420999));
        verify(brasilApiCepGeocodingService).geocodePostalCode("87000000");
        org.assertj.core.api.Assertions.assertThat(profileRepository.findByEstablishmentId(establishment.getId())
                .orElseThrow().getLatitude()).isEqualByComparingTo("-23.420999");
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

    @Test
    void shouldValidateOptionalPhonesOnProfileUpdate() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Studio Telefone").cnpj("82345678000195").build());
        User admin = user("Admin Telefone", "admin-profile-phone@salonhub.test", UserRole.ADMIN, establishment);

        for (String phone : List.of("abc44999999999", "123", "449999999999", "---")) {
            mockMvc.perform(put("/api/estabelecimentos/me/perfil").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"%s\"}".formatted(phone)))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/estabelecimentos/me/perfil").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"whatsapp\":\"%s\"}".formatted(phone)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(put("/api/estabelecimentos/me/perfil").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"(44) 3333-3333\",\"whatsapp\":\"(44) 99999-9999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("4433333333"))
                .andExpect(jsonPath("$.whatsapp").value("44999999999"));
        mockMvc.perform(put("/api/estabelecimentos/me/perfil").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"\",\"whatsapp\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.whatsapp").isEmpty());
    }
    private User user(String name, String email, UserRole role, Establishment establishment) {
        return userRepository.saveAndFlush(User.builder().name(name).email(email)
                .password("senha-de-teste").role(role).establishment(establishment).active(true).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.generate(user).value();
    }
}
