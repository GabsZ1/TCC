package br.com.salonhub.config;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.dto.appointment.AppointmentRescheduleRequest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica o registro dos validadores usados pela API. */
class ValidationConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TimeConfig.class, ValidationConfig.class, FixedClockConfiguration.class);

    @Test
    void temporalConstraintsUseTheBusinessClockRegardlessOfTheJvmTimezone() {
        TimeZone originalTimezone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
        try {
            contextRunner.run(context -> {
                Validator validator = context.getBean(Validator.class);

                assertThat(validator.validate(new AppointmentCreateRequest(
                        1L, 1L, LocalDateTime.of(2026, 1, 1, 0, 0)))).isEmpty();
                assertThat(validator.validate(new AppointmentRescheduleRequest(
                        null, 1L, LocalDateTime.of(2026, 1, 1, 0, 15)))).isEmpty();
                assertThat(validator.validate(new AppointmentCreateRequest(
                        1L, 1L, LocalDateTime.of(2025, 12, 31, 23, 15)))).isNotEmpty();
            });
        } finally {
            TimeZone.setDefault(originalTimezone);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(
                    LocalDateTime.of(2025, 12, 31, 23, 30).toInstant(ZoneOffset.ofHours(-3)),
                    ZoneOffset.ofHours(-3));
        }
    }
}
