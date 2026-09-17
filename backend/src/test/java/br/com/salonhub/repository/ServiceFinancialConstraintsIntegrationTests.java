package br.com.salonhub.repository;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ApiExceptionHandler;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceFinancialConstraintsIntegrationTests {
    @Autowired EstablishmentRepository establishments;
    @Autowired EstablishmentServiceRepository services;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    private Establishment establishment;

    @BeforeEach
    void setUp() {
        establishment = establishments.saveAndFlush(Establishment.builder()
                .name("Financial constraints").cnpj("95959595000195").build());
    }

    @ParameterizedTest
    @CsvSource({
            "0,10,false,ck_salon_services_price", "-0.01,10,false,ck_salon_services_price",
            "10,-0.01,false,ck_salon_services_commission_percentage", "10,100.01,false,ck_salon_services_commission_percentage",
            "0,10,true,ck_salon_services_price", "-0.01,10,true,ck_salon_services_price",
            "10,-0.01,true,ck_salon_services_commission_percentage", "10,100.01,true,ck_salon_services_commission_percentage"
    })
    void shouldRejectInvalidInsertAndUpdateAtDatabaseLevel(
            BigDecimal price, BigDecimal commission, boolean update, String constraint) {
        EstablishmentService service = service(new BigDecimal("10"), BigDecimal.TEN);
        if (update) services.saveAndFlush(service);
        service.updateDetails("Corte", null, price, commission, 30);
        Throwable thrown = catchThrowable(() -> services.saveAndFlush(service));
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        Throwable cause = thrown;
        while (cause != null && !(cause instanceof ConstraintViolationException)) cause = cause.getCause();
        assertThat(cause).isInstanceOf(ConstraintViolationException.class);
        assertThat(((ConstraintViolationException) cause).getConstraintName()).isEqualTo(constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100"})
    void shouldAcceptPositivePriceAndInclusiveCommissionLimits(BigDecimal commission) {
        EstablishmentService saved = services.saveAndFlush(service(new BigDecimal("0.01"), commission));
        assertThat(saved.getId()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"0,10", "-0.01,10", "10,-0.01", "10,100.01"})
    void shouldKeepJavaValidationReturningBadRequest(BigDecimal price, BigDecimal commission) throws Exception {
        User admin = users.saveAndFlush(User.builder().name("Admin").email("financial@tests.test")
                .password("test").role(UserRole.ADMIN).establishment(establishment).build());
        mvc.perform(post("/api/servicos").with(jwt().jwt(token -> token.subject(admin.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Corte","price":%s,"commissionPercentage":%s,"durationMinutes":30}
                                """.formatted(price, commission)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldTranslateActualDuplicateCnpjViolation() {
        Throwable thrown = catchThrowable(() -> establishments.saveAndFlush(Establishment.builder()
                .name("Duplicate").cnpj(establishment.getCnpj()).build()));
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        var response = new ApiExceptionHandler().handleDataIntegrity((DataIntegrityViolationException) thrown);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("CNPJ já cadastrado.");
    }

    @Test
    void shouldTranslateActualDuplicateEmailViolation() {
        users.saveAndFlush(User.builder().name("First").email("duplicate@tests.test")
                .password("test").role(UserRole.CLIENT).build());
        Throwable thrown = catchThrowable(() -> users.saveAndFlush(User.builder().name("Second")
                .email("duplicate@tests.test").password("test").role(UserRole.CLIENT).build()));
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        var response = new ApiExceptionHandler().handleDataIntegrity((DataIntegrityViolationException) thrown);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("E-mail já cadastrado no sistema.");
    }

    private EstablishmentService service(BigDecimal price, BigDecimal commission) {
        return EstablishmentService.builder().establishment(establishment).name("Corte")
                .price(price).commissionPercentage(commission).durationMinutes(30).build();
    }
}
