/** Tolerância de atraso antes de permitir registrar a falta (igual ao backend). */
import { addMinutesToBusinessLocalDateTime, businessNowLocalDateTime, normalizeBusinessLocalDateTime } from '@/lib/dateTime'

export const NO_SHOW_TOLERANCE_MINUTES = 15

/**
 * A falta pode ser registrada a partir do início + tolerância. `now` pode ser a hora local do
 * estabelecimento como texto ("AAAA-MM-DDTHH:mm:ss"), comparada no mesmo formato de startsAt.
 */
export function canMarkNoShow(startsAt: string, now: Date | string = new Date()) {
  const nowValue = typeof now === 'string' ? normalizeBusinessLocalDateTime(now) : businessNowLocalDateTime(now)
  return addMinutesToBusinessLocalDateTime(startsAt, NO_SHOW_TOLERANCE_MINUTES) <= nowValue
}
