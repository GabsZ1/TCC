package br.com.salonhub.entity;

/** Enumera os estados possíveis no ciclo de vida de uma reserva. */
public enum AppointmentStatus {
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    INVALIDATED,
    NO_SHOW
}
