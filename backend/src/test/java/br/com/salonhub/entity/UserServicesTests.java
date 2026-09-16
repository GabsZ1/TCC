package br.com.salonhub.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserServicesTests {

    @Test
    void shouldOfferAnyServiceWhenConfiguredForAllServices() {
        User professional = User.builder().offersAllServices(true).build();
        EstablishmentService service = EstablishmentService.builder().name("Corte").build();

        assertThat(professional.offers(service)).isTrue();
    }

    @Test
    void shouldOfferOnlyAssignedServicesWhenConfiguredIndividually() {
        EstablishmentService assignedService = EstablishmentService.builder().name("Corte").build();
        EstablishmentService unassignedService = EstablishmentService.builder().name("Barba").build();
        User professional = User.builder().build();
        professional.updateOfferedServices(false, List.of(assignedService));

        assertThat(professional.offers(assignedService)).isTrue();
        assertThat(professional.offers(unassignedService)).isFalse();
        assertThat(professional.offers(null)).isFalse();
    }
}
