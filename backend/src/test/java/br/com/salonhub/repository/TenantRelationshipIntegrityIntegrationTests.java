package br.com.salonhub.repository;

import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TenantRelationshipIntegrityIntegrationTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired EstablishmentRepository establishments;
    @Autowired UserRepository users;
    @Autowired EstablishmentServiceRepository services;
    @Autowired CustomerRepository customers;

    private Establishment firstEstablishment;
    private User firstProfessional;
    private User secondProfessional;
    private EstablishmentService firstService;
    private EstablishmentService secondService;
    private Customer firstCustomer;

    @BeforeEach
    void setUp() {
        firstEstablishment = establishments.saveAndFlush(Establishment.builder()
                .name("First tenant").cnpj("81818181000181").build());
        Establishment secondEstablishment = establishments.saveAndFlush(Establishment.builder()
                .name("Second tenant").cnpj("82828282000182").build());
        firstProfessional = professional("first-professional", firstEstablishment);
        secondProfessional = professional("second-professional", secondEstablishment);
        firstService = service("First service", firstEstablishment);
        secondService = service("Second service", secondEstablishment);
        firstCustomer = customers.saveAndFlush(Customer.create(firstEstablishment, "Customer", "44999999999", null));
    }

    @Test
    void shouldRejectAppointmentWithProfessionalFromAnotherEstablishment() {
        assertThatThrownBy(() -> insertAppointment(secondProfessional.getId(), firstService.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectAppointmentWithServiceFromAnotherEstablishment() {
        assertThatThrownBy(() -> insertAppointment(firstProfessional.getId(), secondService.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectProfessionalServiceLinkAcrossEstablishments() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO professional_services (professional_id, service_id) VALUES (?, ?)",
                firstProfessional.getId(), secondService.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectCalendarEventWithProfessionalFromAnotherEstablishment() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO professional_calendar_events
                            (establishment_id, professional_id, title, starts_at, ends_at, all_day)
                        VALUES (?, ?, 'Cross tenant', ?, ?, false)
                        """, firstEstablishment.getId(), secondProfessional.getId(), startsAt, startsAt.plusHours(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertAppointment(Long professionalId, Long serviceId) {
        LocalDateTime startsAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        jdbc.update("""
                        INSERT INTO appointments
                            (establishment_id, customer_id, professional_id, service_id, service_name,
                             agreed_price, commission_percentage, commission_amount, duration_minutes,
                             starts_at, ends_at, status)
                        VALUES (?, ?, ?, ?, 'Cross tenant', 10.00, 0.00, 0.00, 30, ?, ?, 'CONFIRMED')
                        """, firstEstablishment.getId(), firstCustomer.getId(), professionalId, serviceId,
                startsAt, startsAt.plusMinutes(30));
    }

    private User professional(String name, Establishment establishment) {
        return users.saveAndFlush(User.builder().name(name).email(name + "@tenant.test")
                .password("test").role(UserRole.PROFESSIONAL).establishment(establishment).build());
    }

    private EstablishmentService service(String name, Establishment establishment) {
        return services.saveAndFlush(EstablishmentService.builder().establishment(establishment)
                .name(name).price(new BigDecimal("10.00")).commissionPercentage(BigDecimal.ZERO)
                .durationMinutes(30).build());
    }
}
