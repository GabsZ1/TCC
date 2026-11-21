package br.com.salonhub.service.report;

/** Identifica a unidade e o administrador que solicitou a exportação em PDF. */
public record ReportHeader(String establishmentName, String issuedBy) {}
