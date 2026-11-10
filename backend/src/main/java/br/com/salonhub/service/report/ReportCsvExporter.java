package br.com.salonhub.service.report;

import br.com.salonhub.dto.commission.CommissionReportEntry;
import br.com.salonhub.dto.commission.CommissionReportResponse;
import br.com.salonhub.dto.report.AppointmentsReportEntry;
import br.com.salonhub.dto.report.AppointmentsReportResponse;
import br.com.salonhub.dto.report.FinancialReportResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte em CSV os MESMOS objetos que a tela recebe, então tela e arquivo nunca divergem.
 * Formato pensado para o Excel em português: separador ";", vírgula decimal e UTF-8 com BOM
 * (para os acentos aparecerem certos).
 */
@Component
public class ReportCsvExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Escreve o relatório de comissões em CSV com colunas e valores próprios do período. */
    public byte[] commission(CommissionReportResponse report) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Relatório de comissões", period(report.periodStart(), report.periodEnd())));
        rows.add(List.of());
        rows.add(List.of("Profissional", "Atendimentos concluídos", "Valor dos serviços (R$)", "Percentual médio (%)", "Comissão (R$)"));
        for (CommissionReportEntry entry : report.entries()) {
            rows.add(List.of(entry.professionalName(), String.valueOf(entry.completedServicesCount()),
                    money(entry.totalServiceValue()), money(entry.averageCommissionPercentage()), money(entry.totalCommission())));
        }
        rows.add(List.of("Total", "", "", "", money(report.totalCommission())));
        return toCsv(rows);
    }

    /** Escreve o resumo financeiro em CSV para análise fora da aplicação. */
    public byte[] financial(FinancialReportResponse report) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Relatório financeiro", period(report.periodStart(), report.periodEnd())));
        rows.add(List.of());
        rows.add(List.of("Faturamento bruto (R$)", "Comissões (R$)", "Valor após comissões (R$)", "Atendimentos concluídos", "Ticket médio (R$)"));
        rows.add(List.of(money(report.grossRevenue()), money(report.totalCommission()), money(report.revenueAfterCommission()),
                String.valueOf(report.completedCount()), money(report.averageTicket())));
        appendLines(rows, "Serviço", report.byService());
        appendLines(rows, "Profissional", report.byProfessional());
        return toCsv(rows);
    }

    /** Escreve os agendamentos filtrados em CSV preservando o cabeçalho do relatório. */
    public byte[] appointments(AppointmentsReportResponse report) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Relatório de agenda e comparecimento", period(report.periodStart(), report.periodEnd())));
        rows.add(List.of());
        rows.add(List.of("Profissional", "Confirmados", "Concluídos", "Cancelados", "Faltas", "Invalidados", "Total",
                "Taxa de conclusão (%)", "Taxa de comparecimento (%)", "Taxa de cancelamento (%)", "Taxa de falta (%)"));
        for (AppointmentsReportEntry entry : report.entries()) {
            rows.add(List.of(entry.professionalName(), String.valueOf(entry.confirmedCount()), String.valueOf(entry.completedCount()),
                    String.valueOf(entry.cancelledCount()), String.valueOf(entry.noShowCount()), String.valueOf(entry.invalidatedCount()),
                    String.valueOf(entry.totalCount()), money(entry.completionRate()), money(entry.attendanceRate()),
                    money(entry.cancellationRate()), money(entry.noShowRate())));
        }
        long total = report.totalConfirmed() + report.totalCompleted() + report.totalCancelled()
                + report.totalNoShows() + report.totalInvalidated();
        rows.add(List.of("Total", String.valueOf(report.totalConfirmed()), String.valueOf(report.totalCompleted()),
                String.valueOf(report.totalCancelled()), String.valueOf(report.totalNoShows()), String.valueOf(report.totalInvalidated()),
                String.valueOf(total), money(report.completionRate()), money(report.attendanceRate()),
                money(report.cancellationRate()), money(report.noShowRate())));
        return toCsv(rows);
    }

    private static void appendLines(List<List<String>> rows, String label, List<FinancialReportResponse.Line> lines) {
        rows.add(List.of());
        rows.add(List.of(label, "Atendimentos concluídos", "Faturamento (R$)", "Comissões (R$)", "Ticket médio (R$)"));
        for (FinancialReportResponse.Line line : lines) {
            rows.add(List.of(line.name(), String.valueOf(line.completedCount()), money(line.revenue()),
                    money(line.commission()), money(line.averageTicket())));
        }
    }

    private static String period(LocalDate start, LocalDate end) {
        return "Período: " + DATE.format(start) + " a " + DATE.format(end);
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static byte[] toCsv(List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        for (List<String> row : rows) {
            csv.append(String.join(";", row.stream().map(ReportCsvExporter::escape).toList())).append("\r\n");
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(content, 0, withBom, BOM.length, content.length);
        return withBom;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        // Evita que o Excel interprete o texto como fórmula (injeção de CSV).
        String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.contains(";") || safe.contains("\"") || safe.contains("\n")
                ? "\"" + safe.replace("\"", "\"\"") + "\""
                : safe;
    }
}
