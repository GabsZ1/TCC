package br.com.salonhub.service;

import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.dto.establishment.EstablishmentUpdateRequest;
import br.com.salonhub.dto.establishment.BusinessHourRequest;
import br.com.salonhub.dto.establishment.BusinessHourResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstablishmentManagementService {

    private final EstablishmentRepository establishmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EstablishmentResponse findMine(Long administratorId) {
        return response(findEstablishment(administratorId));
    }

    @Transactional
    public EstablishmentResponse update(Long administratorId, EstablishmentUpdateRequest request) {
        Establishment identityEstablishment = findEstablishment(administratorId);
        Establishment establishment = establishmentRepository.findByIdForUpdate(identityEstablishment.getId())
                .orElseThrow(() -> new IllegalStateException("Estabelecimento do administrador não encontrado."));
        if (!establishment.isActive()) {
            throw new ConflictException("Não é possível editar um estabelecimento desativado.");
        }

        if (request.businessHours() != null) {
            validateBusinessHours(request.businessHours());
            requireNoConflictingConfirmedAppointments(establishment, request.businessHours());
        }

        establishment.updateRegistration(
                request.name().trim(),
                normalizeOptionalText(request.email())
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
        return response(establishment);
    }

    private Establishment findEstablishment(Long administratorId) {
        return tenantAuthorizationService.requireAdministrator(administratorId).getEstablishment();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void updateBusinessHours(Establishment establishment, java.util.List<BusinessHourRequest> requests) {
        requests.forEach(request -> {
            EstablishmentBusinessHour hour = businessHourRepository
                    .findByEstablishmentIdAndDayOfWeek(establishment.getId(), request.dayOfWeek())
                    .orElseGet(() -> EstablishmentBusinessHour.builder()
                            .establishment(establishment).dayOfWeek(request.dayOfWeek()).build());
            hour.update(request.closed(), request.openingTime(), request.closingTime());
            if (hour.getId() == null) {
                businessHourRepository.save(hour);
            }
        });
    }

    private void validateBusinessHours(List<BusinessHourRequest> requests) {
        if (requests.stream().map(BusinessHourRequest::dayOfWeek).distinct().count() != 7) {
            throw new InvalidRequestException("Informe cada dia da semana uma única vez.");
        }
        requests.forEach(request -> {
            if (!request.closed() && (request.openingTime() == null || request.closingTime() == null
                    || !request.openingTime().isBefore(request.closingTime()))) {
                throw new InvalidRequestException("O horário de abertura deve ser anterior ao fechamento.");
            }
        });
    }

    /**
     * Impede reduzir o expediente de forma que um atendimento confirmado futuro ou em
     * andamento fique fora do novo horário. O estabelecimento é bloqueado antes desta
     * verificação, usando a mesma ordem de lock da criação de agendamentos, para que uma
     * nova reserva não possa entrar entre a validação e a gravação do expediente.
     */
    private void requireNoConflictingConfirmedAppointments(
            Establishment establishment, List<BusinessHourRequest> requests) {
        List<Appointment> relevantConfirmed = appointmentRepository
                .findByEstablishmentIdAndStatusAndEndsAtGreaterThanOrderByStartsAt(
                        establishment.getId(), AppointmentStatus.CONFIRMED, LocalDateTime.now(clock));
        if (relevantConfirmed.isEmpty()) {
            return;
        }

        Map<DayOfWeek, BusinessHourRequest> hoursByDay = requests.stream()
                .collect(Collectors.toMap(BusinessHourRequest::dayOfWeek, Function.identity()));
        long conflicting = relevantConfirmed.stream()
                .filter(appointment -> fallsOutsideNewBusinessHours(appointment, hoursByDay))
                .count();
        if (conflicting > 0) {
            throw new ConflictException(
                    "Não foi possível alterar o expediente: " + conflicting
                            + " agendamento(s) confirmado(s) futuro(s) ou em andamento ficariam fora do novo horário. "
                            + "Cancele-os, aguarde a conclusão ou ajuste o expediente para mantê-los dentro do horário.");
        }
    }

    private boolean fallsOutsideNewBusinessHours(
            Appointment appointment, Map<DayOfWeek, BusinessHourRequest> hoursByDay) {
        BusinessHourRequest hour = hoursByDay.get(appointment.getStartsAt().getDayOfWeek());
        if (hour == null || hour.closed()) {
            return true;
        }
        LocalTime opening = hour.openingTime();
        LocalTime closing = hour.closingTime();
        boolean changesDay = !appointment.getStartsAt().toLocalDate()
                .equals(appointment.getEndsAt().toLocalDate());
        return changesDay
                || appointment.getStartsAt().toLocalTime().isBefore(opening)
                || appointment.getEndsAt().toLocalTime().isAfter(closing);
    }

    private EstablishmentResponse response(Establishment establishment) {
        var hours = businessHourRepository.findByEstablishmentIdOrderByDayOfWeek(establishment.getId())
                .stream().map(BusinessHourResponse::fromEntity).toList();
        return EstablishmentResponse.fromEntity(establishment, hours);
    }
}
