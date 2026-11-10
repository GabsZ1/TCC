package br.com.salonhub.service.report;

import br.com.salonhub.dto.commission.CommissionReportEntry;
import br.com.salonhub.dto.commission.CommissionReportResponse;
import br.com.salonhub.dto.report.AppointmentsReportEntry;
import br.com.salonhub.dto.report.AppointmentsReportResponse;
import br.com.salonhub.dto.report.CompletedAppointmentEntry;
import br.com.salonhub.dto.report.CompletedAppointmentsResponse;
import br.com.salonhub.dto.report.FinancialReportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exporta os mesmos dados usados pelas telas, com paginação e quebra de texto. */
@Component
public class ReportPdfExporter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    /** Monta o PDF de comissões com resumo e linhas do período. */
    public byte[] commission(CommissionReportResponse report) {
        return render("Relatório de comissões", report.periodStart(), report.periodEnd(), pdf -> {
            pdf.metric("Comissão total", money(report.totalCommission()));
            pdf.section("Comissões por profissional");
            if (report.entries().isEmpty()) pdf.empty();
            for (CommissionReportEntry entry : report.entries()) {
                pdf.row(entry.professionalName(), entry.completedServicesCount() + " atendimentos concluídos  |  Serviços: "
                        + money(entry.totalServiceValue()) + "  |  Percentual médio: "
                        + percent(entry.averageCommissionPercentage()), money(entry.totalCommission()));
            }
        });
    }

    /** Monta o PDF financeiro com totais e detalhamento disponíveis. */
    public byte[] financial(FinancialReportResponse report) {
        return render("Relatório financeiro", report.periodStart(), report.periodEnd(), pdf -> {
            pdf.metric("Faturamento bruto", money(report.grossRevenue()));
            pdf.metric("Comissões", money(report.totalCommission()));
            pdf.metric("Valor após comissões", money(report.revenueAfterCommission()));
            pdf.metric("Atendimentos concluídos", String.valueOf(report.completedCount()));
            pdf.metric("Ticket médio", money(report.averageTicket()));
            pdf.section("Por serviço");
            if (report.byService().isEmpty()) pdf.empty();
            for (FinancialReportResponse.Line line : report.byService()) financialLine(pdf, line);
            pdf.section("Por profissional");
            if (report.byProfessional().isEmpty()) pdf.empty();
            for (FinancialReportResponse.Line line : report.byProfessional()) financialLine(pdf, line);
            pdf.note("Valor após comissões não representa lucro; despesas e impostos não estão incluídos.");
        });
    }

    /** Monta o PDF de agendamentos a partir dos filtros aplicados. */
    public byte[] appointments(AppointmentsReportResponse report) {
        return render("Agenda e comparecimento", report.periodStart(), report.periodEnd(), pdf -> {
            pdf.metric("Confirmados", String.valueOf(report.totalConfirmed()));
            pdf.metric("Concluídos", String.valueOf(report.totalCompleted()));
            pdf.metric("Cancelados", String.valueOf(report.totalCancelled()));
            pdf.metric("Faltas", String.valueOf(report.totalNoShows()));
            pdf.metric("Invalidados", String.valueOf(report.totalInvalidated()));
            pdf.metric("Conclusão / comparecimento", percent(report.completionRate()) + " / " + percent(report.attendanceRate()));
            pdf.metric("Cancelamento / falta", percent(report.cancellationRate()) + " / " + percent(report.noShowRate()));
            pdf.section("Por profissional");
            if (report.entries().isEmpty()) pdf.empty();
            for (AppointmentsReportEntry entry : report.entries()) {
                pdf.row(entry.professionalName(), "Confirmados: " + entry.confirmedCount() + "  |  Concluídos: "
                        + entry.completedCount() + "  |  Cancelados: " + entry.cancelledCount() + "  |  Faltas: "
                        + entry.noShowCount() + "  |  Invalidados: " + entry.invalidatedCount()
                        + "\nConclusão: " + percent(entry.completionRate()) + "  |  Comparecimento: "
                        + percent(entry.attendanceRate()) + "  |  Cancelamento: "
                        + percent(entry.cancellationRate()) + "  |  Falta: " + percent(entry.noShowRate()),
                        entry.totalCount() + " no total");
            }
            pdf.note("As taxas consideram desfechos. Invalidados não entram nas taxas; comparecimento = concluídos / (concluídos + faltas).");
        });
    }

    /** Monta o PDF dos atendimentos concluídos do estabelecimento. */
    public byte[] completed(CompletedAppointmentsResponse report, List<CompletedAppointmentEntry> entries) {
        return render("Atendimentos realizados", report.startDate(), report.endDate(), pdf -> {
            pdf.metric("Atendimentos finalizados", String.valueOf(report.totalElements()));
            pdf.metric("Valor total", money(report.totalServiceValue()));
            pdf.metric("Total de comissões", money(report.totalCommission()));
            pdf.section("Atendimentos");
            if (entries.isEmpty()) pdf.empty();
            for (CompletedAppointmentEntry entry : entries) {
                pdf.row(entry.professionalName() + " - " + entry.serviceName(),
                        "Concluído em " + dateTime(entry.completedAt()) + "  |  Cliente: " + entry.clientName()
                                + "  |  Comissão: " + money(entry.commissionAmount()) + " ("
                                + percent(entry.commissionPercentage()) + ")",
                        money(entry.servicePrice()));
            }
        });
    }

    private static void financialLine(ReportDocument pdf, FinancialReportResponse.Line line) throws IOException {
        pdf.row(line.name(), line.completedCount() + " atendimentos concluídos  |  Comissões: "
                + money(line.commission()) + "  |  Ticket médio: " + money(line.averageTicket()), money(line.revenue()));
    }

    private byte[] render(String title, LocalDate start, LocalDate end, PdfContent content) {
        try (ReportDocument pdf = new ReportDocument(title, start, end)) {
            content.write(pdf);
            return pdf.bytes();
        } catch (IOException error) {
            throw new UncheckedIOException("Não foi possível gerar o relatório em PDF.", error);
        }
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value == null ? BigDecimal.ZERO : value);
    }

    private static String percent(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value) + "%";
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @FunctionalInterface
    private interface PdfContent { void write(ReportDocument pdf) throws IOException; }

    private static final class ReportDocument implements AutoCloseable {
        private static final float MARGIN = 42;
        private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private static final float BOTTOM = 55;
        private static final Color NAVY = new Color(25, 43, 75);
        private static final Color MUTED = new Color(86, 99, 118);
        private static final Color LIGHT = new Color(230, 235, 242);
        private final PDDocument document = new PDDocument();
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final String title;
        private final String period;
        private PDPageContentStream stream;
        private float y;

        ReportDocument(String title, LocalDate start, LocalDate end) throws IOException {
            this.title = title;
            this.period = start == null || end == null ? "Período: todo o histórico"
                    : "Período: " + DATE.format(start) + " a " + DATE.format(end);
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            stream.setNonStrokingColor(NAVY);
            stream.addRect(0, PDRectangle.A4.getHeight() - 104, PDRectangle.A4.getWidth(), 104);
            stream.fill();
            text(title, MARGIN, PDRectangle.A4.getHeight() - 55, bold, 18, Color.WHITE);
            text(period, MARGIN, PDRectangle.A4.getHeight() - 77, regular, 10, Color.WHITE);
            y = PDRectangle.A4.getHeight() - 132;
        }

        private void ensure(float height) throws IOException {
            if (y - height < BOTTOM) newPage();
        }

        void metric(String label, String value) throws IOException {
            ensure(31);
            text(label, MARGIN, y, regular, 10, MUTED);
            text(value, MARGIN + WIDTH - width(value, bold, 11), y, bold, 11, NAVY);
            rule(y - 10);
            y -= 31;
        }

        void section(String heading) throws IOException {
            ensure(44);
            y -= 15;
            text(heading, MARGIN, y, bold, 13, NAVY);
            y -= 23;
        }

        void row(String name, String detail, String amount) throws IOException {
            float amountWidth = Math.min(130, width(amount, bold, 10) + 8);
            List<String> nameLines = wrap(name, bold, 10, WIDTH - amountWidth - 18);
            List<String> detailLines = wrap(detail, regular, 9, WIDTH);
            float height = 13 + 13 * nameLines.size() + 12 * detailLines.size() + 9;
            ensure(height);
            float lineY = y - 13;
            for (String line : nameLines) { text(line, MARGIN + 8, lineY, bold, 10, NAVY); lineY -= 13; }
            text(amount, MARGIN + WIDTH - width(amount, bold, 10), y - 13, bold, 10, NAVY);
            for (String line : detailLines) { text(line, MARGIN + 8, lineY, regular, 9, MUTED); lineY -= 12; }
            rule(y - height + 5);
            y -= height;
        }

        void empty() throws IOException { note("Nenhum dado encontrado para os filtros selecionados."); }

        void note(String value) throws IOException {
            List<String> lines = wrap(value, regular, 9, WIDTH);
            ensure(lines.size() * 12 + 16);
            y -= 8;
            for (String line : lines) { text(line, MARGIN, y, regular, 9, MUTED); y -= 12; }
        }

        private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : value.split("\\n", -1)) {
                String current = "";
                for (String word : safe(paragraph, font).split("\\s+")) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (width(candidate, font, size) <= maxWidth) { current = candidate; continue; }
                    if (!current.isEmpty()) { lines.add(current); current = ""; }
                    for (int index = 0; index < word.length(); index++) {
                        String next = current + word.charAt(index);
                        if (width(next, font, size) > maxWidth && !current.isEmpty()) {
                            lines.add(current);
                            current = "" + word.charAt(index);
                        } else current = next;
                    }
                }
                lines.add(current);
            }
            return lines;
        }

        private void rule(float atY) throws IOException {
            stream.setStrokingColor(LIGHT);
            stream.setLineWidth(0.7f);
            stream.moveTo(MARGIN, atY);
            stream.lineTo(MARGIN + WIDTH, atY);
            stream.stroke();
        }

        private float width(String value, PDFont font, float size) throws IOException {
            return font.getStringWidth(safe(value, font)) * size / 1000;
        }

        private String safe(String value, PDFont font) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (Character.isISOControl(character)) { result.append(' '); continue; }
                try { font.getStringWidth(String.valueOf(character)); result.append(character); }
                catch (IllegalArgumentException | IOException ignored) { result.append('?'); }
            }
            return result.toString();
        }

        private void text(String value, float x, float atY, PDFont font, float size, Color color) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, atY);
            stream.showText(safe(value, font));
            stream.endText();
        }

        byte[] bytes() throws IOException {
            stream.close();
            stream = null;
            int total = document.getNumberOfPages();
            for (int index = 0; index < total; index++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(index),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.beginText();
                    footer.setFont(regular, 9);
                    footer.setNonStrokingColor(MUTED);
                    footer.newLineAtOffset(MARGIN, 30);
                    footer.showText("Meu Espaço  |  " + (index + 1) + " / " + total);
                    footer.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }

        @Override public void close() throws IOException {
            if (stream != null) stream.close();
            document.close();
        }
    }
}
