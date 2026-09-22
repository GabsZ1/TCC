package br.com.salonhub.controller;

import br.com.salonhub.dto.commission.CommissionReportResponse;
import br.com.salonhub.dto.dashboard.DashboardSummaryResponse;
import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.dto.establishment.EstablishmentUpdateRequest;
import br.com.salonhub.dto.report.AppointmentsReportResponse;
import br.com.salonhub.service.AppointmentsReportService;
import br.com.salonhub.service.CommissionReportService;
import br.com.salonhub.service.DashboardService;
import br.com.salonhub.service.EstablishmentManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/estabelecimentos/me")
@RequiredArgsConstructor
public class EstablishmentController {

    private final EstablishmentManagementService establishmentManagementService;
    private final DashboardService dashboardService;
    private final CommissionReportService commissionReportService;
    private final AppointmentsReportService appointmentsReportService;

    @GetMapping
    public EstablishmentResponse findMine(@AuthenticationPrincipal Jwt jwt) {
        return establishmentManagementService.findMine(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/dashboard")
    public DashboardSummaryResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.getSummary(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/relatorio-comissoes")
    public CommissionReportResponse commissionReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId) {
        return commissionReportService.getReport(Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId);
    }

    @GetMapping("/relatorio-atendimentos")
    public AppointmentsReportResponse appointmentsReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId) {
        return appointmentsReportService.getReport(Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId);
    }

    @PutMapping
    public EstablishmentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentUpdateRequest request) {
        return establishmentManagementService.update(Long.valueOf(jwt.getSubject()), request);
    }

    @PatchMapping("/bloqueio-agendamentos")
    public EstablishmentResponse blockBookings(@AuthenticationPrincipal Jwt jwt) {
        return establishmentManagementService.blockBookings(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/bloqueio-agendamentos")
    public EstablishmentResponse allowBookings(@AuthenticationPrincipal Jwt jwt) {
        return establishmentManagementService.allowBookings(Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/pausa")
    public EstablishmentResponse pause(@AuthenticationPrincipal Jwt jwt) {
        return establishmentManagementService.pause(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/pausa")
    public EstablishmentResponse resume(@AuthenticationPrincipal Jwt jwt) {
        return establishmentManagementService.resume(Long.valueOf(jwt.getSubject()));
    }
}
