package br.com.salonhub.service.professionalagenda;

import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.ProfessionalBusinessHour;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.ProfessionalBusinessHourRepository;
import br.com.salonhub.repository.ProfessionalCalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class ProfessionalAvailabilityService {

    private final ProfessionalBusinessHourRepository professionalHourRepository;
    private final EstablishmentBusinessHourRepository establishmentHourRepository;
    private final ProfessionalCalendarEventRepository eventRepository;

    public WorkingHours requireWorkingHours(User professional, LocalDate date) {
        ProfessionalBusinessHour ownHour = professionalHourRepository
                .findByProfessionalIdAndDayOfWeek(professional.getId(), date.getDayOfWeek())
                .orElse(null);
        if (ownHour != null) {
            if (ownHour.isClosed()) {
                throw new ConflictException("O profissional não trabalha nesta data.");
            }
            return new WorkingHours(ownHour.getOpeningTime(), ownHour.getClosingTime());
        }
        EstablishmentBusinessHour inherited = establishmentHourRepository
                .findByEstablishmentIdAndDayOfWeek(
                        professional.getEstablishment().getId(), date.getDayOfWeek())
                .filter(hour -> !hour.isClosed())
                .orElseThrow(() -> new ConflictException("O estabelecimento está fechado nesta data."));
        return new WorkingHours(inherited.getOpeningTime(), inherited.getClosingTime());
    }

    public void validateAvailable(User professional, LocalDateTime startsAt, LocalDateTime endsAt) {
        WorkingHours hours = requireWorkingHours(professional, startsAt.toLocalDate());
        boolean changesDay = !startsAt.toLocalDate().equals(endsAt.toLocalDate());
        if (changesDay || startsAt.toLocalTime().isBefore(hours.openingTime())
                || endsAt.toLocalTime().isAfter(hours.closingTime())) {
            throw new ConflictException("O horário está fora da jornada do profissional.");
        }
        if (eventRepository.existsByProfessionalIdAndStartsAtLessThanAndEndsAtGreaterThan(
                professional.getId(), endsAt, startsAt)) {
            throw new ConflictException("O profissional possui um bloqueio pessoal nesse período.");
        }
    }

    public record WorkingHours(LocalTime openingTime, LocalTime closingTime) {
    }
}
