package br.com.salonhub.service.geocoding;
import org.springframework.stereotype.Component;
/** Calcula distâncias entre coordenadas para ordenar resultados por proximidade. */
@Component
public class GeographicDistanceCalculator {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    /** Calcula a distância aproximada entre dois pontos a partir de latitude e longitude. */
    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
