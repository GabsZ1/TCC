package br.com.salonhub.repository;

/**
 * Projeção usada por {@link AppointmentRepository#findAppointmentsReportRows}
 * para agregar, por profissional, a quantidade de agendamentos em cada
 * situação dentro do período do relatório de atendimentos.
 */
public interface AppointmentsReportRow {
    Long getProfessionalId();
    String getProfessionalName();
    long getConfirmedCount();
    long getCompletedCount();
    long getCancelledCount();
    long getInvalidatedCount();
    long getNoShowCount();
}
