/** Consulta e altera a agenda, os horários e os eventos do profissional. */
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
  status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'INVALIDATED' | 'NO_SHOW' | null
  clientName: string | null
  clientEmail: string | null
  serviceName: string | null
  durationMinutes: number | null
  agreedPrice: number | null
  cancellationReason: string | null
  invalidationReason: string | null
}

const agendaPath = (professionalId?: number) => professionalId == null ? '/profissionais/me/agenda' : `/funcionarios/${professionalId}/agenda`
export function fetchProfessionalAgenda(startsAt: string, endsAt: string, professionalId?: number) {
  return api.get<ProfessionalAgendaItem[]>(agendaPath(professionalId), { params: { startsAt, endsAt } }).then((response) => response.data)
}
export function fetchProfessionalSchedule(professionalId?: number) { return api.get<ProfessionalHour[]>(`${agendaPath(professionalId)}/horarios`).then((response) => response.data) }
export function fetchProfessionalAgendaConfiguration(professionalId?: number) { return api.get<ProfessionalAgendaConfiguration>(`${agendaPath(professionalId)}/configuracao`).then((response) => response.data) }
export function updateProfessionalSchedule(businessHours: ProfessionalHourInput[], professionalId?: number) { return api.put<ProfessionalHour[]>(`${agendaPath(professionalId)}/horarios`, { businessHours }).then((response) => response.data) }
export function createCalendarEvent(input: CalendarEventInput, professionalId?: number) { return api.post<CalendarEvent>(`${agendaPath(professionalId)}/eventos`, input).then((response) => response.data) }
export function updateCalendarEvent(eventId: number, input: CalendarEventInput, professionalId?: number) { return api.put<CalendarEvent>(`${agendaPath(professionalId)}/eventos/${eventId}`, input).then((response) => response.data) }
export function deleteCalendarEvent(eventId: number, professionalId?: number) { return api.delete(`${agendaPath(professionalId)}/eventos/${eventId}`) }
