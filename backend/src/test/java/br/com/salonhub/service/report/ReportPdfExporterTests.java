package br.com.salonhub.service.report;

import br.com.salonhub.dto.commission.CommissionReportEntry;
import br.com.salonhub.dto.commission.CommissionReportResponse;
import br.com.salonhub.dto.report.AppointmentsReportEntry;
import br.com.salonhub.dto.report.AppointmentsReportResponse;
import br.com.salonhub.dto.report.CompletedAppointmentEntry;
import br.com.salonhub.dto.report.CompletedAppointmentsResponse;
import br.com.salonhub.dto.report.FinancialReportResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica a estrutura e o conteúdo dos PDFs de relatório. */
class ReportPdfExporterTests {
    private final ReportPdfExporter exporter = new ReportPdfExporter();
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Test
    void exportsEveryReportWithItsFiltersAndTotals() throws Exception {
        var commission = new CommissionReportResponse(START, END,
                List.of(new CommissionReportEntry(1L, "Ana Souza", 3, decimal("300"), decimal("20"), decimal("60"))),
                decimal("60"));
        var financial = new FinancialReportResponse(START, END, 3, decimal("300"), decimal("60"),
                decimal("240"), decimal("100"),
                List.of(new FinancialReportResponse.Line(1L, "Corte feminino", 3, decimal("300"), decimal("60"), decimal("100"))),
                List.of(new FinancialReportResponse.Line(1L, "Ana Souza", 3, decimal("300"), decimal("60"), decimal("100"))));
        var attendance = new AppointmentsReportResponse(START, END,
                List.of(new AppointmentsReportEntry(1L, "Ana Souza", 1, 3, 1, 0, 1, 6,
                        decimal("60"), decimal("75"), decimal("20"), decimal("20"))),
                1, 3, 1, 0, 1, decimal("60"), decimal("75"), decimal("20"), decimal("20"));
        var completed = new CompletedAppointmentsResponse(START, END, 1L, List.of(), 0, 100, 1,
                1, decimal("100"), decimal("20"));
        var entry = new CompletedAppointmentEntry(1L, LocalDateTime.of(2026, 9, 10, 14, 30), 1L,
                "Ana Souza", 1L, "Corte feminino", "Maria Silva", decimal("100"), decimal("20"), decimal("20"));

        assertPdfContains(exporter.commission(commission), "Ana Souza", "Comissão total");
        byte[] financialPdf = exporter.financial(financial);
        assertPdfContains(financialPdf, "Corte feminino", "Valor após comissões", "01/09/2026");
        assertPdfContains(exporter.appointments(attendance), "Ana Souza", "Comparecimento", "Invalidados");
        assertPdfContains(exporter.completed(completed, List.of(entry)), "Maria Silva", "Corte feminino", "14:30");

        if (Boolean.getBoolean("pdf.preview")) Files.write(Path.of("target", "report-preview.pdf"), financialPdf);
    }

    @Test
    void wrapsLongNamesAndPaginatesWithoutLosingEntries() throws Exception {
        List<CommissionReportEntry> entries = new ArrayList<>();
        for (int index = 0; index < 80; index++) {
            entries.add(new CommissionReportEntry((long) index, "Profissional " + index + " com nome muito longo para a coluna",
                    2, decimal("100"), decimal("20"), decimal("20")));
        }
        byte[] bytes = exporter.commission(new CommissionReportResponse(START, END, entries, decimal("1600")));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Profissional 0", "Profissional 79", "1 / ");
        }
    }

    private static void assertPdfContains(byte[] bytes, String... expected) throws Exception {
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains(expected);
        }
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
