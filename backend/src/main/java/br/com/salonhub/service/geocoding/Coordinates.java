package br.com.salonhub.service.geocoding;
import java.math.BigDecimal;
/** Transporta latitude e longitude usadas nas buscas por proximidade. */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}
