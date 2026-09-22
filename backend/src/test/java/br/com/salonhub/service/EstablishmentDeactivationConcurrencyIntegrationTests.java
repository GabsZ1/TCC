package br.com.salonhub.service;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.appointment.AppointmentCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EstablishmentDeactivationConcurrencyIntegrationTests {

    @Autowired private AppointmentCommandService appointmentCommandService;
    @Autowired private EstablishmentManagementService establishmentManagementService;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private EstablishmentBusinessHourRepository businessHourRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void deactivationMustBlockOnlyAppointmentsThatHaveNotAlreadyBeenCreated() throws Exception {
        String unique = UUID.randomUUID().toString();
        String cnpj = Long.toString(Math.abs(unique.hashCode()) + 10_000_000_000_000L);
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão concorrência").cnpj(cnpj).build());
        User administrator = userRepository.saveAndFlush(user("admin-race-" + unique, UserRole.ADMIN, establishment));
        User professional = userRepository.saveAndFlush(user("professional-race-" + unique, UserRole.PROFESSIONAL, establishment));
        User client = userRepository.saveAndFlush(user("client-race-" + unique, UserRole.CLIENT, null));
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Serviço concorrência").price(new BigDecimal("80.00"))
                .commissionPercentage(new BigDecimal("10.00")).durationMinutes(30).active(true).build());
        LocalDateTime startsAt = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        businessHourRepository.saveAndFlush(EstablishmentBusinessHour.builder()
                .establishment(establishment).dayOfWeek(startsAt.getDayOfWeek()).closed(false)
                .openingTime(LocalTime.of(8, 0)).closingTime(LocalTime.of(18, 0)).build());

        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> creation = executor.submit(() -> {
                start.await();
                try {
                    appointmentCommandService.create(client.getId(), establishment.getId(),
                            new AppointmentCreateRequest(service.getId(), professional.getId(), startsAt));
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            });
            Future<Boolean> deactivation = executor.submit(() -> {
                start.await();
                try {
                    establishmentManagementService.deactivate(administrator.getId());
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            });

            boolean creationSucceeded = creation.get();
            boolean deactivationSucceeded = deactivation.get();
            assertThat(deactivationSucceeded).isTrue();

            Establishment current = establishmentRepository.findById(establishment.getId()).orElseThrow();
            long appointments = appointmentRepository.findAll().stream()
                    .filter(item -> item.getEstablishment().getId().equals(establishment.getId()))
                    .count();
            assertThat(current.isActive()).isFalse();
            assertThat(appointments).isEqualTo(creationSucceeded ? 1 : 0);
        }
    }

    private User user(String key, UserRole role, Establishment establishment) {
        return User.builder().name(key).email(key + "@salonhub-race.test")
                .password("senha-de-teste").role(role).establishment(establishment).active(true).build();
    }
}
