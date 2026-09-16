package br.com.salonhub.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentSnapshotTests {

    @Test
    void shouldKeepFinancialSnapshotAfterServiceChanges() {
        EstablishmentService service = EstablishmentService.builder()
                .name("Corte original")
                .price(new BigDecimal("50.00"))
                .commissionPercentage(new BigDecimal("10.00"))
                .durationMinutes(60)
                .build();
        Appointment appointment = Appointment.schedule(
                Establishment.builder().name("Salão").build(),
                User.builder().name("Cliente").build(),
                User.builder().name("Profissional").build(),
                service,
                LocalDateTime.of(2026, 1, 1, 10, 0));
        service.updateDetails(
                "Corte reajustado",
                service.getDescription(),
                new BigDecimal("80.00"),
                new BigDecimal("20.00"),
                90);

        assertThat(appointment.getServiceName()).isEqualTo("Corte original");
        assertThat(appointment.getAgreedPrice()).isEqualByComparingTo("50.00");
        assertThat(appointment.getCommissionPercentage()).isEqualByComparingTo("10.00");
        assertThat(appointment.getCommissionAmount()).isEqualByComparingTo("5.00");
        assertThat(appointment.getDurationMinutes()).isEqualTo(60);
    }
}
