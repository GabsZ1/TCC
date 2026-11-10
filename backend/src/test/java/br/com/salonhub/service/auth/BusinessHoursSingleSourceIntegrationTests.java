package br.com.salonhub.service.auth;

import br.com.salonhub.dto.auth.InitialRegistrationRequest;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica a fonte única dos horários de funcionamento. */
@SpringBootTest
@Transactional
class BusinessHoursSingleSourceIntegrationTests {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private EstablishmentBusinessHourRepository businessHourRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUseOnlyWeeklyBusinessHoursAndCreateTheCurrentDefaults() {
        var registration = registrationService.registerEstablishment(new InitialRegistrationRequest(
                "Administrador", "single-source@salonhub.test", "senha123",
                "Salão Fonte Única", "11222333000181", null, null));

        var establishment = establishmentRepository.findById(registration.establishmentId()).orElseThrow();
        assertThat(establishment.getMinimumAdvanceMinutes()).isEqualTo(120);
        assertThat(establishment.getMaximumAdvanceDays()).isEqualTo(60);

        Integer legacyColumnCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'establishments'
                  AND column_name IN ('opening_time', 'closing_time')
                """, Integer.class);
        assertThat(legacyColumnCount).isZero();

        var weeklyHours = businessHourRepository
                .findByEstablishmentIdOrderByDayOfWeek(registration.establishmentId());
        assertThat(weeklyHours).hasSize(7).allSatisfy(hour -> {
            assertThat(hour.isClosed()).isFalse();
            assertThat(hour.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(hour.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
        });
    }
}
