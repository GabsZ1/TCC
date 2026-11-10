package br.com.salonhub.service.report;

import br.com.salonhub.exception.InvalidRequestException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** Período dos relatórios: as duas datas juntas, ou o mês corrente quando nenhuma é informada. */
public record ReportPeriod(LocalDate start, LocalDate end) {

    /** Exige as duas datas juntas ou usa o mês corrente como período padrão. */
    public static ReportPeriod resolve(LocalDate startDate, LocalDate endDate, Clock clock) {
        if ((startDate == null) != (endDate == null)) {
            throw new InvalidRequestException("Informe a data inicial e a data final para filtrar por período.");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new InvalidRequestException("A data inicial não pode ser posterior à data final.");
        }
        if (startDate != null) {
            return new ReportPeriod(startDate, endDate);
        }
        YearMonth currentMonth = YearMonth.now(clock);
        return new ReportPeriod(currentMonth.atDay(1), currentMonth.atEndOfMonth());
    }

    /** Converte a data inicial para o início incluído na consulta. */
    public LocalDateTime startInclusive() {
        return start.atStartOfDay();
    }

    /** Converte a data final para o primeiro instante fora do período. */
    public LocalDateTime endExclusive() {
        return end.plusDays(1).atStartOfDay();
    }
}
