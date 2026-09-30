package br.com.salonhub.controller;

import br.com.salonhub.dto.commission.CommissionReportResponse;
import br.com.salonhub.dto.dashboard.DashboardSummaryResponse;
import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.dto.establishment.EstablishmentUpdateRequest;
import br.com.salonhub.dto.report.AppointmentsReportResponse;
import br.com.salonhub.dto.report.CompletedAppointmentsResponse;
import br.com.salonhub.dto.report.FinancialReportResponse;
import br.com.salonhub.service.AppointmentsReportService;
import br.com.salonhub.service.CommissionReportService;
import br.com.salonhub.service.CompletedAppointmentsService;
import br.com.salonhub.service.DashboardService;
import br.com.salonhub.service.EstablishmentManagementService;
import br.com.salonhub.service.EstablishmentOperationService;
import br.com.salonhub.service.FinancialReportService;
import br.com.salonhub.service.ReportCsvExporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/estabelecimentos/me")
@RequiredArgsConstructor
public class EstablishmentController {

    private final EstablishmentManagementService establishmentManagementService;
    private final EstablishmentOperationService establishmentOperationService;
    private final DashboardService dashboardService;
    private final CommissionReportService commissionReportService;
    private final AppointmentsReportService appointmentsReportService;
    private final CompletedAppointmentsService completedAppointmentsService;
    private final FinancialReportService financialReportService;
    private final ReportCsvExporter csvExporter;

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

    @GetMapping("/relatorio-comissoes/csv")
    public ResponseEntity<byte[]> commissionReportCsv(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId) {
        CommissionReportResponse report = commissionReportService.getReport(
                Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId);
        return csv("relatorio-comissoes", report.periodStart(), report.periodEnd(), csvExporter.commission(report));
    }

    /** Relatório financeiro/produção: faturamento, comissões, valor após comissões e ticket médio. */
    @GetMapping("/relatorio-financeiro")
    public FinancialReportResponse financialReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId,
            @RequestParam(required = false) Long serviceId) {
        return financialReportService.getReport(Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId, serviceId);
    }

    @GetMapping("/relatorio-financeiro/csv")
    public ResponseEntity<byte[]> financialReportCsv(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId,
            @RequestParam(required = false) Long serviceId) {
        FinancialReportResponse report = financialReportService.getReport(
                Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId, serviceId);
        return csv("relatorio-financeiro", report.periodStart(), report.periodEnd(), csvExporter.financial(report));
    }

    @GetMapping("/atendimentos-realizados")
    public CompletedAppointmentsResponse completedAppointments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return completedAppointmentsService.find(
                Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId, page, size);
    }

    @GetMapping("/relatorio-atendimentos")
    public AppointmentsReportResponse appointmentsReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId) {
        return appointmentsReportService.getReport(Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId);
    }

    @GetMapping("/relatorio-atendimentos/csv")
    public ResponseEntity<byte[]> appointmentsReportCsv(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long professionalId) {
        AppointmentsReportResponse report = appointmentsReportService.getReport(
                Long.valueOf(jwt.getSubject()), startDate, endDate, professionalId);
        return csv("relatorio-comparecimento", report.periodStart(), report.periodEnd(), csvExporter.appointments(report));
    }

    private static ResponseEntity<byte[]> csv(String name, LocalDate start, LocalDate end, byte[] content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name + "_" + start + "_" + end + ".csv").build().toString())
                .body(content);
    }

    @PutMapping
    public EstablishmentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentUpdateRequest request) {
        return establishmentManagementService.update(Long.valueOf(jwt.getSubject()), request);
    }

    @PatchMapping("/bloqueio-agendamentos")
    public EstablishmentResponse blockBookings(@AuthenticationPrincipal Jwt jwt) {
        return establishmentOperationService.blockBookings(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/bloqueio-agendamentos")
    public EstablishmentResponse allowBookings(@AuthenticationPrincipal Jwt jwt) {
        return establishmentOperationService.allowBookings(Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/pausa")
    public EstablishmentResponse pause(@AuthenticationPrincipal Jwt jwt) {
        return establishmentOperationService.pause(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/pausa")
    public EstablishmentResponse resume(@AuthenticationPrincipal Jwt jwt) {
        return establishmentOperationService.resume(Long.valueOf(jwt.getSubject()));
    }
}
