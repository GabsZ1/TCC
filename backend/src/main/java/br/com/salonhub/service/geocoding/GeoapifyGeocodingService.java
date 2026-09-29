package br.com.salonhub.service.geocoding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
@Service
public class GeoapifyGeocodingService implements GeocodingService {
    private final String apiKey; private final RestClient client;
    public GeoapifyGeocodingService(@Value("${app.geocoding.geoapify.api-key:}") String apiKey,
            @Value("${app.geocoding.geoapify.base-url:https://api.geoapify.com}") String baseUrl) {
        this.apiKey = apiKey; this.client = RestClient.builder().baseUrl(baseUrl).build();
    }
    public Optional<Coordinates> geocode(String address) {
        if (apiKey == null || apiKey.isBlank() || address == null || address.isBlank()) return Optional.empty();
        try {
            Response response = client.get().uri(b -> b.path("/v1/geocode/search").queryParam("text", address)
                    .queryParam("format", "json").queryParam("limit", 1).queryParam("apiKey", apiKey).build())
                    .retrieve().body(Response.class);
            if (response == null || response.results() == null || response.results().isEmpty()) return Optional.empty();
            Result result = response.results().getFirst();
            return result.lat() == null || result.lon() == null ? Optional.empty() : Optional.of(new Coordinates(result.lat(), result.lon()));
        } catch (RuntimeException ignored) { return Optional.empty(); }
    }
    private record Response(List<Result> results) {} private record Result(BigDecimal lat, BigDecimal lon) {}
}
