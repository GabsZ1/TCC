package br.com.salonhub.repository;

import java.math.BigDecimal;

/**
 * Projeção usada por {@link AppointmentRepository#findCommissionReportRows}
 * para agregar, por funcionário, os atendimentos concluídos no período do
 * relatório de comissões (US 2.21).
 */
public interface CommissionReportRow {
    Long getProfessionalId();
    String getProfessionalName();
    long getCompletedServicesCount();
    BigDecimal getTotalServiceValue();
    BigDecimal getTotalCommission();
}
