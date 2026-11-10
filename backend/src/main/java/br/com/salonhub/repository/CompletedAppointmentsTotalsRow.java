package br.com.salonhub.repository;

import java.math.BigDecimal;

/** Declara as consultas de agendamentos usadas pela camada de serviço. */
public interface CompletedAppointmentsTotalsRow {

    Long getTotalAppointments();

    BigDecimal getTotalServiceValue();

    BigDecimal getTotalCommission();
}
