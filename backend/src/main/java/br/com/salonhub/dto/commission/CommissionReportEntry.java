package br.com.salonhub.dto.commission;

import java.math.BigDecimal;

/**
 * Linha do relatório de comissões (US 2.21): totais de um único
 * funcionário dentro do período consultado. Como a comissão é
 * configurada por serviço (não por funcionário), um funcionário que
 * realizou tipos de serviço diferentes pode ter comissões percentuais
 * diferentes entre os atendimentos — {@code averageCommissionPercentage}
 * é a taxa efetiva (comissão total / valor total dos serviços), não a
 * porcentagem de um serviço específico.
 */
public record CommissionReportEntry(
        Long professionalId,
        String professionalName,
        long completedServicesCount,
        BigDecimal totalServiceValue,
        BigDecimal averageCommissionPercentage,
        BigDecimal totalCommission) {
}
