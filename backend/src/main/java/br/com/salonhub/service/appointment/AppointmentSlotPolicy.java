package br.com.salonhub.service.appointment;

import java.time.LocalDateTime;

final class AppointmentSlotPolicy {

    static final int SLOT_INTERVAL_MINUTES = 30;
    static final int INTERNAL_SLOT_INTERVAL_MINUTES = 15;

    private AppointmentSlotPolicy() {
    }

    static boolean isAligned(LocalDateTime startsAt) {
        return isAligned(startsAt, SLOT_INTERVAL_MINUTES);
    }

    static boolean isAligned(LocalDateTime startsAt, int intervalMinutes) {
        return startsAt.getMinute() % intervalMinutes == 0
                && startsAt.getSecond() == 0
                && startsAt.getNano() == 0;
    }
}
