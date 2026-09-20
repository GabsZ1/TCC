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

    /**
     * Horário efetivo do profissional em uma data: a interseção entre o horário de
     * funcionamento do estabelecimento e o horário próprio do profissional, quando existir.
     * Isso evita que uma mudança no horário do salão deixe de ser respeitada só porque o
     * profissional já tinha personalizado a própria jornada anteriormente.
     */
    public WorkingHours requireWorkingHours(User professional, LocalDate date) {
        EstablishmentBusinessHour establishmentHour = establishmentHourRepository
                .findByEstablishmentIdAndDayOfWeek(
                        professional.getEstablishment().getId(), date.getDayOfWeek())
                .filter(hour -> !hour.isClosed())
                .orElseThrow(() -> new ConflictException("O estabelecimento está fechado nesta data."));

        ProfessionalBusinessHour ownHour = professionalHourRepository
                .findByProfessionalIdAndDayOfWeek(professional.getId(), date.getDayOfWeek())
                .orElse(null);
        if (ownHour == null) {
            return new WorkingHours(establishmentHour.getOpeningTime(), establishmentHour.getClosingTime());
        }
        if (ownHour.isClosed()) {
            throw new ConflictException("O profissional não trabalha nesta data.");
        }

        LocalTime opening = laterOf(establishmentHour.getOpeningTime(), ownHour.getOpeningTime());
        LocalTime closing = earlierOf(establishmentHour.getClosingTime(), ownHour.getClosingTime());
        if (!opening.isBefore(closing)) {
            throw new ConflictException("O profissional não possui horário disponível nesta data.");
        }
        return new WorkingHours(opening, closing);
    }

    private LocalTime laterOf(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalTime earlierOf(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
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
