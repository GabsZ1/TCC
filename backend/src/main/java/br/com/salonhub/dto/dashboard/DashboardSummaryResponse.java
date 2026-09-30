package br.com.salonhub.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Indicadores exibidos no dashboard do administrador (US 2.14):
 * quantos serviços foram concluídos hoje, o total de comissões já
 * geradas no mês corrente e quantos funcionários estão ativos.
 */
public record DashboardSummaryResponse(
        long todaysCompletedServices,
        BigDecimal monthlyCommissionTotal,
        long activeEmployees,
        long todaysAppointments,
        long pendingClosure,
        BigDecimal todaysRevenue,
        List<UpcomingAppointment> upcomingAppointments) {
    public record UpcomingAppointment(Long id, LocalDateTime startsAt, String customerName,
                                      String serviceName, String professionalName) {}
}
