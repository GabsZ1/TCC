package br.com.salonhub.service.catalog;

import br.com.salonhub.dto.catalog.CatalogProfessionalResponse;
import br.com.salonhub.dto.catalog.CatalogServiceResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Monta o catálogo público de serviços e profissionais disponíveis. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;

    /** Expõe apenas serviços ativos do estabelecimento no catálogo público. */
    @Transactional(readOnly = true)
    public List<CatalogServiceResponse> findServices(Long establishmentId) {
        requireBookableEstablishment(establishmentId);
        return serviceRepository.findByEstablishmentIdAndActiveTrue(establishmentId)
                .stream()
                .map(CatalogServiceResponse::fromEntity)
                .toList();
    }

    /** Filtra profissionais ativos que oferecem o serviço selecionado. */
    @Transactional(readOnly = true)
    public List<CatalogProfessionalResponse> findProfessionals(Long establishmentId, Long serviceId) {
        requireBookableEstablishment(establishmentId);
        if (serviceId != null) {
            serviceRepository.findByIdAndEstablishmentIdAndActiveTrue(serviceId, establishmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
        }
        return userRepository.findActiveProfessionalsForService(establishmentId, UserRole.PROFESSIONAL, serviceId)
                .stream()
                .map(CatalogProfessionalResponse::fromEntity)
                .toList();
    }

    private Establishment requireBookableEstablishment(Long establishmentId) {
        return establishmentRepository.findById(establishmentId)
                .filter(Establishment::acceptsNewAppointments)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
    }
}
