package br.com.salonhub.controller;

import br.com.salonhub.dto.commission.CommissionReportResponse;
import br.com.salonhub.service.report.CommissionReportService;
import br.com.salonhub.service.report.ReportPdfExporter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica a geração de PDF para os dados do estabelecimento. */
class EstablishmentPdfControllerTests {
    @Test
    void downloadsPdfWithSameFiltersAsReport() {
        CommissionReportService service = mock(CommissionReportService.class);
        ReportPdfExporter exporter = mock(ReportPdfExporter.class);
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        CommissionReportResponse report = new CommissionReportResponse(start, end, List.of(), BigDecimal.ZERO);
        when(service.getReport(7L, start, end, 11L)).thenReturn(report);
        when(exporter.commission(report)).thenReturn("%PDF-test".getBytes());
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("7");
        EstablishmentController controller = new EstablishmentController(
                null, null, null, service, null, null, null, null, exporter);

        var response = controller.commissionReportPdf(jwt, start, end, 11L);

        verify(service).getReport(7L, start, end, 11L);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("relatorio-comissoes_2026-09-01_2026-09-30.pdf");
        assertThat(response.getBody()).isEqualTo("%PDF-test".getBytes());
    }
}
