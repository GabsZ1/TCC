package br.com.salonhub.service;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.entity.AppointmentStatus;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppointmentStateTransitionConcurrencyIntegrationTests {

    private static final AtomicLong UNIQUE = new AtomicLong(System.currentTimeMillis() % 10_000_000_000_000L);

    @Autowired private AppointmentCommandService commandService;
    @Autowired private EstablishmentOperationService establishmentOperationService;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private EstablishmentBusinessHourRepository businessHourRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void administrativeCancellationAndCompletionMustSerialize() throws Exception {
        Fixture fixture = fixture(LocalDateTime.now().minusMinutes(15));

        PairResult result = race(
                () -> commandService.cancelAsAdministrator(
                        fixture.administrator().getId(), fixture.appointment().getId(), "Cancelamento concorrente"),
                () -> commandService.complete(
                        fixture.professional().getId(), fixture.appointment().getId()));

        assertThat(result.successCount()).isEqualTo(1);
        Appointment current = reload(fixture);
        assertThat(current.getStatus()).isIn(AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED);
        if (current.getStatus() == AppointmentStatus.CANCELLED) {
            assertThat(current.getCancellationReason()).isEqualTo("Cancelamento concorrente");
            assertThat(current.getCancelledAt()).isNotNull();
            assertThat(current.getCancelledBy()).isNotNull();
            assertThat(current.getCompletedAt()).isNull();
        } else {
            assertThat(current.getCompletedAt()).isNotNull();
            assertThat(current.getCancellationReason()).isNull();
            assertThat(current.getCancelledAt()).isNull();
            assertThat(current.getCancelledBy()).isNull();
        }
    }

    @Test
    void clientCancellationAndReschedulingMustNotLoseTheCancellation() throws Exception {
        LocalDateTime originalStart = futureAlignedStart();
        Fixture fixture = fixture(originalStart);
        LocalDateTime rescheduledStart = originalStart.plusMinutes(30);
        ensureBusinessHours(fixture.establishment(), rescheduledStart);

        PairResult result = race(
                () -> commandService.cancel(fixture.client().getId(), fixture.appointment().getId()),
                () -> commandService.update(
                        fixture.client().getId(),
                        fixture.appointment().getId(),
                        new AppointmentCreateRequest(
                                fixture.service().getId(),
                                fixture.professional().getId(),
                                rescheduledStart)));

        assertThat(result.successCount()).isBetween(1, 2);
        Appointment current = reload(fixture);
        if (result.firstSucceeded()) {
            assertThat(current.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        } else {
            assertThat(result.secondSucceeded()).isTrue();
            assertThat(current.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
            assertThat(current.getStartsAt()).isEqualTo(rescheduledStart);
        }
    }

    @Test
    void completionAndEstablishmentDeactivationMustSerialize() throws Exception {
        Fixture fixture = fixture(LocalDateTime.now().minusMinutes(15));
        User systemAdministrator = saveUser(UserRole.SYSTEM_ADMIN, null);

        PairResult result = race(
                () -> commandService.complete(
                        fixture.professional().getId(), fixture.appointment().getId()),
                () -> {
                    establishmentOperationService.deactivateAsSystemAdministrator(
                            systemAdministrator.getId(), fixture.establishment().getId());
                    return null;
                });

        assertThat(result.secondSucceeded()).isTrue();
        assertThat(establishmentRepository.findById(fixture.establishment().getId()).orElseThrow().isActive())
                .isFalse();

        Appointment current = reload(fixture);
        assertThat(current.getStatus()).isIn(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED);
        if (current.getStatus() == AppointmentStatus.COMPLETED) {
            assertThat(result.firstSucceeded()).isTrue();
            assertThat(current.getCompletedAt()).isNotNull();
            assertThat(current.getCancellationReason()).isNull();
        } else {
            assertThat(result.firstSucceeded()).isFalse();
            assertThat(current.getCompletedAt()).isNull();
            assertThat(current.getCancellationReason()).isEqualTo("Estabelecimento desativado");
            assertThat(current.getCancelledBy()).isNotNull();
        }
    }

    @Test
    void twoIncompatibleCancellationsMustLeaveOneCoherentAuditResult() throws Exception {
        Fixture fixture = fixture(futureAlignedStart());

        PairResult result = race(
                () -> commandService.cancel(fixture.client().getId(), fixture.appointment().getId()),
                () -> commandService.cancelAsAdministrator(
                        fixture.administrator().getId(), fixture.appointment().getId(), "Cancelado pelo administrador"));

        assertThat(result.successCount()).isEqualTo(1);
        Appointment current = reload(fixture);
        assertThat(current.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        if (result.secondSucceeded()) {
            assertThat(current.getCancellationReason()).isEqualTo("Cancelado pelo administrador");
            assertThat(current.getCancelledAt()).isNotNull();
            assertThat(current.getCancelledBy()).isNotNull();
        } else {
            assertThat(current.getCancellationReason()).isNull();
            assertThat(current.getCancelledAt()).isNotNull();
            assertThat(current.getCancelledBy()).isNotNull();
        }
    }

    private PairResult race(Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executor.submit(() -> invokeAtBarrier(barrier, first));
            Future<Boolean> secondResult = executor.submit(() -> invokeAtBarrier(barrier, second));
            return new PairResult(firstResult.get(), secondResult.get());
        }
    }

    private boolean invokeAtBarrier(CyclicBarrier barrier, Callable<?> action) throws Exception {
        barrier.await();
        try {
            action.call();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Fixture fixture(LocalDateTime startsAt) {
        long unique = UNIQUE.incrementAndGet();
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Concorrência " + unique)
                .cnpj(String.format("%014d", unique))
                .build());
        User administrator = saveUser(UserRole.ADMIN, establishment);
        User professional = saveUser(UserRole.PROFESSIONAL, establishment);
        User client = saveUser(UserRole.CLIENT, null);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment)
                .name("Serviço " + unique)
                .price(new BigDecimal("80.00"))
                .commissionPercentage(new BigDecimal("10.00"))
                .durationMinutes(30)
                .active(true)
                .build());
        ensureBusinessHours(establishment, startsAt);
        Appointment appointment = appointmentRepository.saveAndFlush(
                schedule(establishment, client, professional, service, startsAt));
        return new Fixture(establishment, administrator, professional, client, service, appointment);
    }

    private User saveUser(UserRole role, Establishment establishment) {
        long unique = UNIQUE.incrementAndGet();
        return userRepository.saveAndFlush(User.builder()
                .name(role.name() + " " + unique)
                .email(role.name().toLowerCase() + "-" + unique + "@concurrency.test")
                .password("senha-de-teste")
                .role(role)
                .establishment(establishment)
                .active(true)
                .build());
    }

    private void ensureBusinessHours(Establishment establishment, LocalDateTime startsAt) {
        if (businessHourRepository
                .findByEstablishmentIdAndDayOfWeek(establishment.getId(), startsAt.getDayOfWeek())
                .isPresent()) {
            return;
        }
        businessHourRepository.saveAndFlush(EstablishmentBusinessHour.builder()
                .establishment(establishment)
                .dayOfWeek(startsAt.getDayOfWeek())
                .closed(false)
                .openingTime(LocalTime.MIDNIGHT)
                .closingTime(LocalTime.of(23, 59))
                .build());
    }

    private LocalDateTime futureAlignedStart() {
        return LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private Appointment reload(Fixture fixture) {
        return appointmentRepository.findById(fixture.appointment().getId()).orElseThrow();
    }

    private record Fixture(
            Establishment establishment,
            User administrator,
            User professional,
            User client,
            EstablishmentService service,
            Appointment appointment) {
    }

    private record PairResult(boolean firstSucceeded, boolean secondSucceeded) {
        int successCount() {
            return (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CustomerRepository customerRepository;

    private Appointment schedule(br.com.salonhub.entity.Establishment establishment,
            br.com.salonhub.entity.User client, br.com.salonhub.entity.User professional,
            br.com.salonhub.entity.EstablishmentService service, java.time.LocalDateTime startsAt) {
        Customer customer = customerRepository.findByEstablishmentIdAndUserId(establishment.getId(), client.getId())
                .orElseGet(() -> {
                    Customer created = Customer.create(establishment, client.getName(), client.getPhone(), client.getEmail());
                    created.link(client);
                    return customerRepository.saveAndFlush(created);
                });
        return Appointment.schedule(establishment, customer, professional, service, startsAt);
    }}
