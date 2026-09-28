package br.com.salonhub.repository;

import java.math.BigDecimal;

public interface CompletedAppointmentsTotalsRow {

    Long getTotalAppointments();

    BigDecimal getTotalServiceValue();

    BigDecimal getTotalCommission();
}
