package br.com.salonhub.service.appointment;

import java.time.LocalDateTime;

final class AppointmentSlotPolicy {

    static final int SLOT_INTERVAL_MINUTES = 30;

    private AppointmentSlotPolicy() {
    }

    static boolean isAligned(LocalDateTime startsAt) {
        return startsAt.getMinute() % SLOT_INTERVAL_MINUTES == 0
                && startsAt.getSecond() == 0
                && startsAt.getNano() == 0;
    }
}
