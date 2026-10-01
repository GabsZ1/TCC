package br.com.salonhub.service.geocoding;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrasilApiCepGeocodingServiceTests {
    @Test
    void readsCoordinatesFromCepV2Response() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://brasilapi.com.br");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://brasilapi.com.br/api/cep/v2/87000000"))
                .andRespond(withSuccess("""
                        {"location":{"type":"Point","coordinates":{"latitude":"-23.420999","longitude":"-51.933056"}}}
                        """, MediaType.APPLICATION_JSON));

        var coordinates = new BrasilApiCepGeocodingService(builder.build()).geocodePostalCode("87000000");

        assertThat(coordinates).isPresent();
        assertThat(coordinates.orElseThrow().latitude()).isEqualByComparingTo("-23.420999");
        assertThat(coordinates.orElseThrow().longitude()).isEqualByComparingTo("-51.933056");
        server.verify();
    }

    @Test
    void ignoresCepWithoutCoordinates() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://brasilapi.com.br");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://brasilapi.com.br/api/cep/v2/87000000"))
                .andRespond(withSuccess("""
                        {"location":{"coordinates":{}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(new BrasilApiCepGeocodingService(builder.build()).geocodePostalCode("87000000")).isEmpty();
        server.verify();
    }
}
