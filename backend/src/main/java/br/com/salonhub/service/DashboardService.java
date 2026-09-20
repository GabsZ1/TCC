package br.com.salonhub.service;

import br.com.salonhub.dto.dashboard.DashboardSummaryResponse;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final Clock clock;

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

        return new DashboardSummaryResponse(
                todaysCompletedServices,
                monthlyCommissionTotal == null ? BigDecimal.ZERO.setScale(2) : monthlyCommissionTotal,
                activeEmployees);
    }
}
