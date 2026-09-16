package br.com.salonhub.service;

import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.dto.establishment.EstablishmentUpdateRequest;
import br.com.salonhub.dto.establishment.BusinessHourRequest;
import br.com.salonhub.dto.establishment.BusinessHourResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EstablishmentManagementService {

    private final EstablishmentRepository establishmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentBusinessHourRepository businessHourRepository;

    @Transactional(readOnly = true)
    public EstablishmentResponse findMine(Long administratorId) {
        return response(findEstablishment(administratorId));
    }

    @Transactional
    public EstablishmentResponse update(Long administratorId, EstablishmentUpdateRequest request) {
        Establishment establishment = findEstablishment(administratorId);
        if (!establishment.isActive()) {
            throw new ConflictException("Não é possível editar um estabelecimento inativo.");
        }

        establishment.updateRegistration(
                request.name().trim(),
                normalizeOptionalText(request.email()),
                normalizeOptionalText(request.phone())
        );
        if (request.businessHours() != null) {
            updateBusinessHours(establishment, request.businessHours());
        }
        if (request.minimumAdvanceHours() != null && request.maximumAdvanceDays() != null) {
            int minimumMinutes = request.minimumAdvanceHours() * 60;
            if (minimumMinutes > request.maximumAdvanceDays() * 1440) {
                throw new InvalidRequestException("A antecedência mínima não pode ser maior que a janela máxima de agendamento.");
            }
            establishment.updateAppointmentAdvancePolicy(minimumMinutes, request.maximumAdvanceDays());
        }
        return response(establishmentRepository.save(establishment));
    }

    @Transactional
    public void deactivate(Long administratorId) {
        Establishment establishment = findEstablishment(administratorId);
        if (establishment.isActive()) {
            establishment.deactivate();
            establishmentRepository.save(establishment);
        }
    }

    private Establishment findEstablishment(Long administratorId) {
        return tenantAuthorizationService.requireAdministratorIdentity(administratorId).getEstablishment();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void updateBusinessHours(Establishment establishment, java.util.List<BusinessHourRequest> requests) {
        if (requests.stream().map(BusinessHourRequest::dayOfWeek).distinct().count() != 7) {
            throw new InvalidRequestException("Informe cada dia da semana uma única vez.");
        }
        requests.forEach(request -> {
            if (!request.closed() && (request.openingTime() == null || request.closingTime() == null
                    || !request.openingTime().isBefore(request.closingTime()))) {
                throw new InvalidRequestException("O horário de abertura deve ser anterior ao fechamento.");
            }
            EstablishmentBusinessHour hour = businessHourRepository
                    .findByEstablishmentIdAndDayOfWeek(establishment.getId(), request.dayOfWeek())
                    .orElseGet(() -> EstablishmentBusinessHour.builder()
                            .establishment(establishment).dayOfWeek(request.dayOfWeek()).build());
            hour.update(request.closed(), request.openingTime(), request.closingTime());
            businessHourRepository.save(hour);
        });
    }

    private EstablishmentResponse response(Establishment establishment) {
        var hours = businessHourRepository.findByEstablishmentIdOrderByDayOfWeek(establishment.getId())
                .stream().map(BusinessHourResponse::fromEntity).toList();
        return EstablishmentResponse.fromEntity(establishment, hours);
    }
}
