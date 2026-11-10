/** Transforma datas e eventos da agenda em estruturas adequadas à visualização. */
import type { CalendarEventInput, ProfessionalAgendaItem } from './professionalAgendaApi'
import { addBusinessDays, addMinutesToBusinessLocalDateTime, businessLocalDateTimeDifferenceMs } from '@/lib/dateTime'

export type AgendaFilter = 'active' | 'all' | 'finished'

export function normalizeEventInput(input: CalendarEventInput): CalendarEventInput {
  if (!input.allDay) return { ...input, startsAt: ensureSeconds(input.startsAt), endsAt: ensureSeconds(input.endsAt) }
  const startDate = input.startsAt.slice(0, 10)
  const selectedEndDate = input.endsAt.slice(0, 10)
  const endDate = selectedEndDate > startDate ? selectedEndDate : addBusinessDays(startDate, 1)
  return { ...input, startsAt: `${startDate}T00:00:00`, endsAt: `${endDate}T00:00:00` }
}

export function filterAgendaItems(items: ProfessionalAgendaItem[], filter: AgendaFilter) {
  if (filter === 'all') return items
  if (filter === 'finished') return items.filter((item) => item.type === 'APPOINTMENT' && item.status !== 'CONFIRMED')
  return items.filter((item) => item.type === 'EVENT' || item.status === 'CONFIRMED')
}

export function findAgendaConflict(items: ProfessionalAgendaItem[], startsAt: string, endsAt: string, ignoredEventId?: number) {
  if (!startsAt || !endsAt || startsAt >= endsAt) return null
  return items.find((item) => {
    if (item.type === 'EVENT' && item.id === ignoredEventId) return false
    if (item.type === 'APPOINTMENT' && item.status !== 'CONFIRMED') return false
    return item.startsAt < ensureSeconds(endsAt) && item.endsAt > ensureSeconds(startsAt)
  }) ?? null
}

export function rangeAfterStartChange(currentStart: string, currentEnd: string, nextStart: string, allDay: boolean) {
  if (!currentEnd) return { startsAt: nextStart, endsAt: minimumEndValue(nextStart, allDay) }
  const minimumDuration = allDay ? 24 * 60 * 60_000 : 5 * 60_000
  const duration = Math.max(minimumDuration, businessLocalDateTimeDifferenceMs(currentStart, currentEnd))
  return { startsAt: nextStart, endsAt: addMinutesToBusinessLocalDateTime(nextStart, duration / 60_000).slice(0, 16) }
}

function ensureSeconds(value: string) { return value.length === 16 ? `${value}:00` : value }
function minimumEndValue(startsAt: string, allDay: boolean) {
  if (!startsAt) return ''
  if (allDay) return `${addBusinessDays(startsAt.slice(0, 10), 1)}T00:00`
  return addMinutesToBusinessLocalDateTime(startsAt, 5).slice(0, 16)
}
