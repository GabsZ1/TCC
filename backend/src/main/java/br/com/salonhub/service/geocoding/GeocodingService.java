package br.com.salonhub.service.geocoding;
import java.util.Optional;
/** Define a interface usada para obter coordenadas de um endereço. */
public interface GeocodingService { Optional<Coordinates> geocode(String completeAddress); }
