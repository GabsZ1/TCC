package br.com.salonhub.service;

import br.com.salonhub.dto.establishment.ActiveEstablishmentResponse;
import br.com.salonhub.repository.EstablishmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActiveEstablishmentService {
    private final EstablishmentRepository establishmentRepository;

    @Transactional(readOnly = true)
    public List<ActiveEstablishmentResponse> findAll() {
        return establishmentRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(establishment -> new ActiveEstablishmentResponse(
                        establishment.getId(),
                        establishment.getName(),
                        establishment.getMinimumAdvanceMinutes() / 60,
                        establishment.getMaximumAdvanceDays()))
                .toList();
    }
}
