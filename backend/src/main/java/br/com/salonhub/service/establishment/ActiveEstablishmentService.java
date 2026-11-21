package br.com.salonhub.service.establishment;
import br.com.salonhub.service.geocoding.GeographicDistanceCalculator;
import br.com.salonhub.service.geocoding.BrasilApiCepGeocodingService;
import br.com.salonhub.dto.establishment.*;
import br.com.salonhub.entity.EstablishmentProfile;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/** Consulta estabelecimentos ativos disponíveis para o cliente. */
@Service @RequiredArgsConstructor
public class ActiveEstablishmentService {
    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentProfileRepository profileRepository;
    private final GeographicDistanceCalculator distanceCalculator;
    private final BrasilApiCepGeocodingService brasilApiCepGeocodingService;
    @Transactional(readOnly=true) public List<NearbyEstablishmentResponse> findNearbyPostalCode(String postalCode) {
        var coordinates = brasilApiCepGeocodingService.geocodePostalCodeForSearch(postalCode)
          .orElseThrow(() -> new InvalidRequestException("Não foi possível confirmar a localização da rua desse CEP. Confira o número ou tente outro CEP."));
        return findNearby(coordinates.latitude().doubleValue(), coordinates.longitude().doubleValue());
    }
    @Transactional(readOnly=true) public List<ActiveEstablishmentResponse> findAll() {
        var establishments=establishmentRepository.findAllByActiveTrueAndPausedFalseAndBookingsBlockedFalseOrderByNameAsc();
        Map<Long,EstablishmentProfile> profiles=profileRepository.findByEstablishmentIdIn(establishments.stream().map(e->e.getId()).toList())
          .stream().collect(Collectors.toMap(p->p.getEstablishment().getId(),Function.identity()));
        return establishments.stream().map(e -> new ActiveEstablishmentResponse(e.getId(),e.getName(),
          e.getMinimumAdvanceMinutes()/60,e.getMaximumAdvanceDays(),
          profiles.get(e.getId()) != null && profiles.get(e.getId()).getLogo() != null)).toList();
    }
    @Transactional(readOnly=true) public List<NearbyEstablishmentResponse> findNearby(double latitude,double longitude) {
        validate(latitude,longitude);
        var establishments=establishmentRepository.findAllByActiveTrueAndPausedFalseAndBookingsBlockedFalseOrderByNameAsc();
        Map<Long,EstablishmentProfile> profiles=profileRepository.findByEstablishmentIdIn(establishments.stream().map(e->e.getId()).toList())
          .stream().collect(Collectors.toMap(p->p.getEstablishment().getId(),Function.identity()));
        return establishments.stream().map(e->{ var p=profiles.get(e.getId()); Double d=p==null||!p.hasLocation()?null:
          distanceCalculator.distanceKm(latitude,longitude,p.getLatitude().doubleValue(),p.getLongitude().doubleValue());
          return new NearbyEstablishmentResponse(e.getId(),e.getName(),e.getMinimumAdvanceMinutes()/60,e.getMaximumAdvanceDays(),d,
            p != null && p.getLogo() != null);
        }).sorted(Comparator.comparing(NearbyEstablishmentResponse::distanceKm,Comparator.nullsLast(Double::compareTo))
          .thenComparing(NearbyEstablishmentResponse::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private void validate(double lat,double lon) {
      if(!Double.isFinite(lat)||lat < -90||lat > 90) throw new InvalidRequestException("Latitude inválida.");
      if(!Double.isFinite(lon)||lon < -180||lon > 180) throw new InvalidRequestException("Longitude inválida.");
    }
}
