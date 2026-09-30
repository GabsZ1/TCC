package br.com.salonhub.repository;

import java.math.BigDecimal;

/** Agregado de atendimentos concluídos por serviço ou por profissional (relatório financeiro). */
public interface FinancialReportRow {
    Long getId();
    String getName();
    long getCompletedCount();
    BigDecimal getRevenue();
    BigDecimal getCommission();
}
