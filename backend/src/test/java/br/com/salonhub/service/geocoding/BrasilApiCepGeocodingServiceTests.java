package br.com.salonhub.service.geocoding;

import br.com.salonhub.exception.CepLookupUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** Verifica a consulta de CEPs e o tratamento de falhas da BrasilAPI. */
class BrasilApiCepGeocodingServiceTests {
    @Test
    void findsDistinctStreetCoordinatesAndRejectsGenericPoints() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://brasilapi.com.br");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient.Builder photonBuilder = RestClient.builder().baseUrl("https://photon.example");
        MockRestServiceServer photon = MockRestServiceServer.bindTo(photonBuilder).build();
        server.expect(once(), requestTo("https://brasilapi.com.br/api/cep/v1/87501130"))
                .andRespond(withSuccess("""
                        {"street":"Avenida Rio Branco","city":"Umuarama","state":"PR"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://brasilapi.com.br/api/cep/v1/87504686"))
                .andRespond(withSuccess("""
                        {"street":"Rua Uirapuru","city":"Umuarama","state":"PR"}
                        """, MediaType.APPLICATION_JSON));
        photon.expect(requestTo(org.hamcrest.Matchers.containsString("q=Avenida%20Rio%20Branco")))
                .andRespond(withSuccess("""
                        {"features":[
                          {"properties":{"type":"city","name":"Umuarama","countrycode":"BR"},
                           "geometry":{"type":"Point","coordinates":[-53.325,-23.76639]}},
                          {"properties":{"type":"street","name":"Avenida Rio Branco","city":"Umuarama","state":"Paraná","countrycode":"BR","postcode":"87501-000"},
                           "geometry":{"type":"Point","coordinates":[-53.3118336,-23.7611938]}},
                          {"properties":{"type":"street","name":"Avenida Rio Branco","city":"Umuarama","state":"Paraná","countrycode":"BR","postcode":"87501-130"},
                           "geometry":{"type":"Point","coordinates":[-53.3126697,-23.7590633]}}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        photon.expect(requestTo(org.hamcrest.Matchers.containsString("q=Rua%20Uirapuru")))
                .andRespond(withSuccess("""
                        {"features":[
                          {"properties":{"type":"city","name":"Umuarama","countrycode":"BR"},
                           "geometry":{"type":"Point","coordinates":[-53.325,-23.76639]}},
                          {"properties":{"type":"street","name":"Rua Uirapuru","city":"Umuarama","state":"Paraná","countrycode":"BR","postcode":"87504-688"},
                           "geometry":{"type":"Point","coordinates":[-53.2903979,-23.7486471]}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var geocoding = new BrasilApiCepGeocodingService(builder.build(), photonBuilder.build());
        var establishment = geocoding.geocodePostalCode("87501130").orElseThrow();
        var customer = geocoding.geocodePostalCodeForSearch("87504686").orElseThrow();
        double distance = new GeographicDistanceCalculator().distanceKm(
                customer.latitude().doubleValue(), customer.longitude().doubleValue(),
                establishment.latitude().doubleValue(), establishment.longitude().doubleValue());

        assertThat(establishment.longitude()).isEqualByComparingTo("-53.3126697");
        assertThat(distance).isBetween(2.5, 2.6);
        server.verify();
        photon.verify();
    }

    @Test
    void ignoresCepWithoutCoordinates() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://brasilapi.com.br");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://brasilapi.com.br/api/cep/v1/87000000"))
                .andRespond(withSuccess("""
                        {"city":"Maringá","state":"PR"}
                        """, MediaType.APPLICATION_JSON));

        RestClient client = builder.build();
        assertThat(new BrasilApiCepGeocodingService(client, client)
                .geocodePostalCode("87000000")).isEmpty();
        server.verify();
    }

    @Test
    void distinguishesUnknownCepFromProviderFailureDuringCustomerSearch() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://brasilapi.com.br");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://brasilapi.com.br/api/cep/v1/87501130"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://brasilapi.com.br/api/cep/v1/87501130"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        RestClient client = builder.build();
        var geocoding = new BrasilApiCepGeocodingService(client, client);

        assertThat(geocoding.geocodePostalCodeForSearch("87501130")).isEmpty();
        assertThatThrownBy(() -> geocoding.geocodePostalCodeForSearch("87501130"))
                .isInstanceOf(CepLookupUnavailableException.class);
        server.verify();
    }
}
