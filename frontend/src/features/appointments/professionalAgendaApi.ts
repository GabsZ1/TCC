import { api } from '@/lib/api'

export type WeekDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface ProfessionalHour {
  dayOfWeek: WeekDay
  closed: boolean
  openingTime: string | null
  closingTime: string | null
  inheritedFromEstablishment: boolean
  establishmentClosed: boolean
  establishmentOpeningTime: string | null
  establishmentClosingTime: string | null
}

/**
 * useEstablishmentHours = true: o dia herda o horário do salão (qualquer personalização
 * anterior é removida no backend e closed/openingTime/closingTime abaixo são ignorados).
 * useEstablishmentHours = false: o dia usa os valores informados de closed/openingTime/closingTime.
 */
export interface ProfessionalHourInput {
  dayOfWeek: WeekDay
  useEstablishmentHours: boolean
  closed: boolean
  openingTime: string | null
  closingTime: string | null
}

export interface CalendarEventInput {
  title: string
  description: string | null
  startsAt: string
  endsAt: string
  allDay: boolean
}

export interface CalendarEvent extends CalendarEventInput { id: number }
export interface ProfessionalAgendaConfiguration { timeZone: string }

export interface ProfessionalAgendaItem {
  type: 'EVENT' | 'APPOINTMENT'
  id: number
  title: string
  description: string | null
  startsAt: string
  endsAt: string
  allDay: boolean
  status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'INVALIDATED' | null
  clientName: string | null
  clientEmail: string | null
  serviceName: string | null
  durationMinutes: number | null
  agreedPrice: number | null
  cancellationReason: string | null
  invalidationReason: string | null
}

export function fetchProfessionalAgenda(startsAt: string, endsAt: string) {
  return api.get<ProfessionalAgendaItem[]>('/profissionais/me/agenda', { params: { startsAt, endsAt } }).then((response) => response.data)
}
export function fetchProfessionalSchedule() { return api.get<ProfessionalHour[]>('/profissionais/me/agenda/horarios').then((response) => response.data) }
export function fetchProfessionalAgendaConfiguration() { return api.get<ProfessionalAgendaConfiguration>('/profissionais/me/agenda/configuracao').then((response) => response.data) }
export function updateProfessionalSchedule(businessHours: ProfessionalHourInput[]) { return api.put<ProfessionalHour[]>('/profissionais/me/agenda/horarios', { businessHours }).then((response) => response.data) }
export function createCalendarEvent(input: CalendarEventInput) { return api.post<CalendarEvent>('/profissionais/me/agenda/eventos', input).then((response) => response.data) }
export function updateCalendarEvent(eventId: number, input: CalendarEventInput) { return api.put<CalendarEvent>(`/profissionais/me/agenda/eventos/${eventId}`, input).then((response) => response.data) }
export function deleteCalendarEvent(eventId: number) { return api.delete(`/profissionais/me/agenda/eventos/${eventId}`) }
