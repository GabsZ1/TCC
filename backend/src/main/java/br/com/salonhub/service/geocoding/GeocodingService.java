package br.com.salonhub.service.geocoding;
import java.util.Optional;
public interface GeocodingService { Optional<Coordinates> geocode(String completeAddress); }
