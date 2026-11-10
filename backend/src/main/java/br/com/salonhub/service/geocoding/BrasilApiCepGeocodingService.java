package br.com.salonhub.service.geocoding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

/** Consulta a localização pública do CEP quando não há geocodificação do endereço completo. */
@Service
public class BrasilApiCepGeocodingService {
    private final RestClient client;

    @Autowired
    public BrasilApiCepGeocodingService(
            @Value("${app.geocoding.brasilapi.base-url:https://brasilapi.com.br}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    BrasilApiCepGeocodingService(RestClient client) {
        this.client = client;
    }

    /** Usa o CEP como alternativa quando a busca pelo endereço completo não encontrou coordenadas. */
    public Optional<Coordinates> geocodePostalCode(String postalCode) {
        if (postalCode == null || !postalCode.matches("\\d{8}")) return Optional.empty();
        try {
            Response response = client.get().uri("/api/cep/v2/{cep}", postalCode)
                    .retrieve().body(Response.class);
            Point point = response == null || response.location() == null ? null : response.location().coordinates();
            if (point == null || point.latitude() == null || point.longitude() == null) return Optional.empty();
            if (point.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                    || point.latitude().compareTo(BigDecimal.valueOf(90)) > 0
                    || point.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                    || point.longitude().compareTo(BigDecimal.valueOf(180)) > 0) return Optional.empty();
            return Optional.of(new Coordinates(point.latitude(), point.longitude()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private record Response(Location location) {}
    private record Location(Point coordinates) {}
    private record Point(BigDecimal latitude, BigDecimal longitude) {}
}
