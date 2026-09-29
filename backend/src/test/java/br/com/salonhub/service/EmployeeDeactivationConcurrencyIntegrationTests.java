package br.com.salonhub.service;

import br.com.salonhub.dto.appointment.AppointmentCreateRequest;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.appointment.AppointmentCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmployeeDeactivationConcurrencyIntegrationTests {

    private static final AtomicLong UNIQUE = new AtomicLong(System.currentTimeMillis() % 10_000_000_000_000L);

    @Autowired private AppointmentCommandService appointmentCommandService;
    @Autowired private EmployeeService employeeService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private EstablishmentBusinessHourRepository businessHourRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void creationHoldingProfessionalLockMustMakeDeactivationObserveTheNewAppointment() throws Exception {
        Fixture fixture = fixture();
        try {
            CountDownLatch creationHoldingLocks = new CountDownLatch(1);
            CountDownLatch releaseCreation = new CountDownLatch(1);
            CountDownLatch deactivationStarted = new CountDownLatch(1);

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<Boolean> creation = executor.submit(() -> inHoldingTransaction(
                        () -> appointmentCommandService.create(
                                fixture.client().getId(),
                                fixture.establishment().getId(),
                                request(fixture)),
                        creationHoldingLocks,
                        releaseCreation));

                assertThat(creationHoldingLocks.await(10, TimeUnit.SECONDS)).isTrue();

                Future<Boolean> deactivation = executor.submit(() -> invoke(
                        () -> employeeService.deactivate(
                                fixture.administrator().getId(), fixture.professional().getId()),
                        deactivationStarted));

                assertThat(deactivationStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(remainsBlocked(deactivation)).isTrue();
                releaseCreation.countDown();

                assertThat(creation.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(deactivation.get(10, TimeUnit.SECONDS)).isFalse();
            }

            assertThat(reloadProfessional(fixture).isActive()).isTrue();
            assertThat(confirmedAppointments(fixture)).hasSize(1);
        } finally {
            cleanUp(fixture);
        }
    }

    @Test
    void deactivationHoldingProfessionalLockMustMakeCreationRejectTheInactiveProfessional() throws Exception {
        Fixture fixture = fixture();
        try {
            CountDownLatch deactivationHoldingLock = new CountDownLatch(1);
            CountDownLatch releaseDeactivation = new CountDownLatch(1);
            CountDownLatch creationStarted = new CountDownLatch(1);

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<Boolean> deactivation = executor.submit(() -> inHoldingTransaction(
                        () -> employeeService.deactivate(
                                fixture.administrator().getId(), fixture.professional().getId()),
                        deactivationHoldingLock,
                        releaseDeactivation));

                assertThat(deactivationHoldingLock.await(10, TimeUnit.SECONDS)).isTrue();

                Future<Boolean> creation = executor.submit(() -> invoke(
                        () -> appointmentCommandService.create(
                                fixture.client().getId(),
                                fixture.establishment().getId(),
                                request(fixture)),
                        creationStarted));

                assertThat(creationStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(remainsBlocked(creation)).isTrue();
                releaseDeactivation.countDown();

                assertThat(deactivation.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(creation.get(10, TimeUnit.SECONDS)).isFalse();
            }

            assertThat(reloadProfessional(fixture).isActive()).isFalse();
            assertThat(confirmedAppointments(fixture)).isEmpty();
        } finally {
            cleanUp(fixture);
        }
    }

    private boolean inHoldingTransaction(
            Callable<?> operation,
            CountDownLatch holdingLocks,
            CountDownLatch release) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    operation.call();
                    holdingLocks.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timeout waiting to release transaction");
                    }
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean invoke(Callable<?> operation, CountDownLatch started) {
        started.countDown();
        try {
            operation.call();
            return true;
        } catch (RuntimeException exception) {
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean remainsBlocked(Future<Boolean> operation) throws InterruptedException {
        Thread.sleep(250);
        return !operation.isDone();
    }

    private AppointmentCreateRequest request(Fixture fixture) {
        return new AppointmentCreateRequest(
                fixture.service().getId(),
                fixture.professional().getId(),
                fixture.startsAt());
    }

    private List<Appointment> confirmedAppointments(Fixture fixture) {
        return appointmentRepository
                .findByProfessionalIdAndStatusAndEndsAtGreaterThanOrderByStartsAt(
                        fixture.professional().getId(),
                        AppointmentStatus.CONFIRMED,
                        LocalDateTime.now());
    }

    private User reloadProfessional(Fixture fixture) {
        return userRepository.findById(fixture.professional().getId()).orElseThrow();
    }

    private Fixture fixture() {
        long unique = UNIQUE.incrementAndGet();
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Employee race " + unique)
                .cnpj(String.format("%014d", unique))
                .build());
        User administrator = saveUser(UserRole.ADMIN, establishment, unique);
        User professional = saveUser(UserRole.PROFESSIONAL, establishment, unique);
        User client = saveUser(UserRole.CLIENT, null, unique);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment)
                .name("Service race " + unique)
                .price(new BigDecimal("70.00"))
                .commissionPercentage(new BigDecimal("10.00"))
                .durationMinutes(30)
                .active(true)
                .build());
        LocalDateTime startsAt = LocalDateTime.now()
                .plusDays(2)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        EstablishmentBusinessHour businessHour = businessHourRepository.saveAndFlush(
                EstablishmentBusinessHour.builder()
                        .establishment(establishment)
                        .dayOfWeek(startsAt.getDayOfWeek())
                        .closed(false)
                        .openingTime(LocalTime.of(8, 0))
                        .closingTime(LocalTime.of(18, 0))
                        .build());
        return new Fixture(
                establishment, administrator, professional, client, service, businessHour, startsAt);
    }

    private User saveUser(UserRole role, Establishment establishment, long unique) {
        return userRepository.saveAndFlush(User.builder()
                .name(role.name() + " race")
                .email(role.name().toLowerCase() + "-" + unique + "@employee-race.test")
                .password("senha-de-teste")
                .role(role)
                .establishment(establishment)
                .active(true)
                .build());
    }

    private void cleanUp(Fixture fixture) {
        appointmentRepository.deleteAll(
                appointmentRepository.findByProfessionalIdOrderByStartsAtDesc(
                        fixture.professional().getId()));
        customerRepository.deleteAll(customerRepository.findByEstablishmentIdOrderByNameAsc(
                fixture.establishment().getId()));
        businessHourRepository.deleteById(fixture.businessHour().getId());
        serviceRepository.deleteById(fixture.service().getId());
        userRepository.deleteAll(List.of(
                fixture.administrator(), fixture.professional(), fixture.client()));
        establishmentRepository.deleteById(fixture.establishment().getId());
    }

    private record Fixture(
            Establishment establishment,
            User administrator,
            User professional,
            User client,
            EstablishmentService service,
            EstablishmentBusinessHour businessHour,
            LocalDateTime startsAt) {
    }
}
