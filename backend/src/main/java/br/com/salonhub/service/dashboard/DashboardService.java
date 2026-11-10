package br.com.salonhub.service.dashboard;

import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.dto.dashboard.DashboardSummaryResponse;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** Calcula os indicadores apresentados no painel administrativo. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final Clock clock;

    /** Reúne os indicadores do estabelecimento para a página inicial administrativa. */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long administratorId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();

        LocalDate today = LocalDate.now(clock);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        YearMonth currentMonth = YearMonth.from(today);
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        long todaysCompletedServices = appointmentRepository
                .countByEstablishmentIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        establishmentId, AppointmentStatus.COMPLETED, todayStart, todayEnd);

        BigDecimal monthlyCommissionTotal = appointmentRepository
                .sumCommissionByEstablishmentIdAndStatusAndCompletedAtBetween(
                        establishmentId, AppointmentStatus.COMPLETED, monthStart, monthEnd);

        long activeEmployees = userRepository
                .countByEstablishmentIdAndRoleAndActiveTrue(establishmentId, UserRole.PROFESSIONAL);

        LocalDateTime now = LocalDateTime.now(clock);
        long todaysAppointments = appointmentRepository
                .countByEstablishmentIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(establishmentId, todayStart, todayEnd);
        long pendingClosure = appointmentRepository
                .countByEstablishmentIdAndStatusAndEndsAtLessThanEqual(establishmentId, AppointmentStatus.CONFIRMED, now);
        BigDecimal todaysRevenue = appointmentRepository.sumRevenueCompletedInPeriod(establishmentId, todayStart, todayEnd);
        var upcoming = appointmentRepository
                .findByEstablishmentIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(
                        establishmentId, AppointmentStatus.CONFIRMED, now, PageRequest.of(0, 5))
                .stream().map(item -> new DashboardSummaryResponse.UpcomingAppointment(item.getId(),
                        item.getStartsAt(), item.getCustomer().getName(), item.getServiceName(),
                        item.getProfessional().getName())).toList();

        return new DashboardSummaryResponse(
                todaysCompletedServices,
                monthlyCommissionTotal == null ? BigDecimal.ZERO.setScale(2) : monthlyCommissionTotal,
                activeEmployees, todaysAppointments, pendingClosure,
                todaysRevenue == null ? BigDecimal.ZERO.setScale(2) : todaysRevenue, upcoming);
    }
}
