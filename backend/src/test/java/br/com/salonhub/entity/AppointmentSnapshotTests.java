package br.com.salonhub.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
        Appointment appointment = Appointment.builder().service(service).build();

        appointment.snapshotService(service);
        service.setName("Corte reajustado");
        service.setPrice(new BigDecimal("80.00"));
        service.setCommissionPercentage(new BigDecimal("20.00"));
        service.setDurationMinutes(90);

        assertThat(appointment.getServiceName()).isEqualTo("Corte original");
        assertThat(appointment.getAgreedPrice()).isEqualByComparingTo("50.00");
        assertThat(appointment.getCommissionPercentage()).isEqualByComparingTo("10.00");
        assertThat(appointment.getCommissionAmount()).isEqualByComparingTo("5.00");
        assertThat(appointment.getDurationMinutes()).isEqualTo(60);
    }
}
