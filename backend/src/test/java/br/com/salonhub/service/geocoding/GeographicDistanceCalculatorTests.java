package br.com.salonhub.service.geocoding;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/** Verifica o cálculo de distância entre coordenadas geográficas. */
class GeographicDistanceCalculatorTests {
    private final GeographicDistanceCalculator calculator = new GeographicDistanceCalculator();
    @Test void sameCoordinateReturnsZero() { assertThat(calculator.distanceKm(-23.420999, -51.933056, -23.420999, -51.933056)).isCloseTo(0, within(0.000001)); }
    @Test void calculatesKnownDistance() { assertThat(calculator.distanceKm(-23.420999, -51.933056, -23.310280, -51.162780)).isBetween(78.0, 81.0); }
    private static org.assertj.core.data.Offset<Double> within(double value) { return org.assertj.core.data.Offset.offset(value); }
}