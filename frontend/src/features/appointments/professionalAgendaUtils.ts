import type { CalendarEventInput, ProfessionalAgendaItem } from './professionalAgendaApi'

export type AgendaFilter = 'active' | 'all' | 'finished'

export function normalizeEventInput(input: CalendarEventInput): CalendarEventInput {
  if (!input.allDay) return { ...input, startsAt: ensureSeconds(input.startsAt), endsAt: ensureSeconds(input.endsAt) }
  const startDate = input.startsAt.slice(0, 10)
  const selectedEndDate = input.endsAt.slice(0, 10)
  const endDate = selectedEndDate > startDate ? selectedEndDate : addDaysToDate(startDate, 1)
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
  const duration = Math.max(minimumDuration, new Date(currentEnd).getTime() - new Date(currentStart).getTime())
  return { startsAt: nextStart, endsAt: toLocalInput(new Date(new Date(nextStart).getTime() + duration)) }
}

function ensureSeconds(value: string) { return value.length === 16 ? `${value}:00` : value }
function toLocalInput(date: Date) { const offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 16) }
function minimumEndValue(startsAt: string, allDay: boolean) {
  if (!startsAt) return ''
  if (allDay) return `${addDaysToDate(startsAt.slice(0, 10), 1)}T00:00`
  const date = new Date(startsAt); date.setMinutes(date.getMinutes() + 5); return toLocalInput(date)
}
function addDaysToDate(value: string, days: number) { const date = new Date(`${value}T12:00:00`); date.setDate(date.getDate() + days); return toLocalInput(date).slice(0, 10) }
