package br.com.salonhub.service.geocoding;

import br.com.salonhub.exception.CepLookupUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Identifica o logradouro pelo CEP e confirma sua localização em dados de mapas. */
@Service
public class BrasilApiCepGeocodingService {
    private final RestClient client;
    private final RestClient streetGeocoder;

    @Autowired
    public BrasilApiCepGeocodingService(
            @Value("${app.geocoding.brasilapi.base-url:https://brasilapi.com.br}") String baseUrl,
            @Value("${app.geocoding.photon.base-url:https://photon.komoot.io}") String photonBaseUrl) {
        this(buildClient(baseUrl), buildClient(photonBaseUrl));
    }

    private static RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    BrasilApiCepGeocodingService(RestClient client, RestClient streetGeocoder) {
        this.client = client;
        this.streetGeocoder = streetGeocoder;
    }

    /** Usa o CEP como alternativa quando a busca pelo endereço completo não encontrou coordenadas. */
    public Optional<Coordinates> geocodePostalCode(String postalCode) {
        if (postalCode == null || !postalCode.matches("\\d{8}")) return Optional.empty();
        try {
            return lookup(postalCode);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Na busca do cliente, diferencia um CEP sem ponto de uma falha temporária do provedor. */
    public Optional<Coordinates> geocodePostalCodeForSearch(String postalCode) {
        if (postalCode == null || !postalCode.matches("\\d{8}")) return Optional.empty();
        try {
            return lookup(postalCode);
        } catch (HttpClientErrorException.NotFound ignored) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new CepLookupUnavailableException(exception);
        }
    }

    private Optional<Coordinates> lookup(String postalCode) {
        Address address = client.get().uri("/api/cep/v1/{cep}", postalCode)
                .retrieve().body(Address.class);
        if (address == null || normalize(address.street()).isEmpty()
                || normalize(address.city()).isEmpty() || normalize(address.state()).isEmpty()) return Optional.empty();

        String query = String.join(", ", address.street(), address.city(), stateName(address.state()));
        StreetResults results = streetGeocoder.get()
                .uri("/api/?q={query}&limit=10", query)
                .retrieve().body(StreetResults.class);
        if (results == null || results.features() == null) return Optional.empty();

        return results.features().stream()
                .filter(feature -> matchesStreet(feature.properties(), address))
                .sorted(Comparator.comparing(feature -> !postalCode.equals(digits(feature.properties().postcode()))))
                .map(Feature::geometry)
                .flatMap(geometry -> coordinatesOf(geometry).stream())
                .findFirst();
    }

    private static boolean matchesStreet(Street street, Address address) {
        return street != null && "street".equals(street.type()) && "BR".equalsIgnoreCase(street.countrycode())
                && normalize(address.street()).equals(normalize(street.name()))
                && normalize(address.city()).equals(normalize(street.city()))
                && (normalize(address.state()).equals(normalize(street.state()))
                    || normalize(stateName(address.state())).equals(normalize(street.state())));
    }

    private static String stateName(String uf) {
        return switch (uf.toUpperCase(Locale.ROOT)) {
            case "AC" -> "Acre"; case "AL" -> "Alagoas"; case "AP" -> "Amapá"; case "AM" -> "Amazonas";
            case "BA" -> "Bahia"; case "CE" -> "Ceará"; case "DF" -> "Distrito Federal";
            case "ES" -> "Espírito Santo"; case "GO" -> "Goiás"; case "MA" -> "Maranhão";
            case "MT" -> "Mato Grosso"; case "MS" -> "Mato Grosso do Sul"; case "MG" -> "Minas Gerais";
            case "PA" -> "Pará"; case "PB" -> "Paraíba"; case "PR" -> "Paraná"; case "PE" -> "Pernambuco";
            case "PI" -> "Piauí"; case "RJ" -> "Rio de Janeiro"; case "RN" -> "Rio Grande do Norte";
            case "RS" -> "Rio Grande do Sul"; case "RO" -> "Rondônia"; case "RR" -> "Roraima";
            case "SC" -> "Santa Catarina"; case "SP" -> "São Paulo"; case "SE" -> "Sergipe"; case "TO" -> "Tocantins";
            default -> uf;
        };
    }

    private static Optional<Coordinates> coordinatesOf(Geometry geometry) {
        if (geometry == null || !"Point".equals(geometry.type())
                || geometry.coordinates() == null || geometry.coordinates().size() < 2) return Optional.empty();
        BigDecimal longitude = geometry.coordinates().get(0);
        BigDecimal latitude = geometry.coordinates().get(1);
        if (latitude == null || longitude == null || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) return Optional.empty();
        return Optional.of(new Coordinates(latitude, longitude));
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private record Address(String street, String city, String state) {}
    private record StreetResults(List<Feature> features) {}
    private record Feature(Geometry geometry, Street properties) {}
    private record Geometry(String type, List<BigDecimal> coordinates) {}
    private record Street(String type, String name, String city, String state, String countrycode, String postcode) {}
}
