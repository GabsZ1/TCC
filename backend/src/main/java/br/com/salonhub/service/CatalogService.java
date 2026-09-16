package br.com.salonhub.service;

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

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CatalogServiceResponse> findServices(Long establishmentId) {
        requireActiveEstablishment(establishmentId);
        return serviceRepository.findByEstablishmentIdAndActiveTrue(establishmentId)
                .stream()
                .map(CatalogServiceResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogProfessionalResponse> findProfessionals(Long establishmentId, Long serviceId) {
        requireActiveEstablishment(establishmentId);
        if (serviceId != null) {
            serviceRepository.findByIdAndEstablishmentIdAndActiveTrue(serviceId, establishmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
        }
        return userRepository.findActiveProfessionalsForService(establishmentId, UserRole.PROFESSIONAL, serviceId)
                .stream()
                .map(CatalogProfessionalResponse::fromEntity)
                .toList();
    }

    private Establishment requireActiveEstablishment(Long establishmentId) {
        return establishmentRepository.findById(establishmentId)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
    }
}
