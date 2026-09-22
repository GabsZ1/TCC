import { api } from '@/lib/api'

export interface Establishment {
  id: number
  name: string
  cnpj: string
  email: string | null
  phone: string | null
  active: boolean
  paused: boolean
  pausedBySystem: boolean
  bookingsBlocked: boolean
  bookingsBlockedBySystem: boolean
  businessHours: BusinessHour[]
  minimumAdvanceHours: number
  maximumAdvanceDays: number
}

export type WeekDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface BusinessHour {
  dayOfWeek: WeekDay
  closed: boolean
  openingTime: string | null
  closingTime: string | null
}

export interface EstablishmentUpdateInput {
  name: string
  email: string
  phone: string
  businessHours: BusinessHour[]
  minimumAdvanceHours: number
  maximumAdvanceDays: number
}

export function fetchMyEstablishment() {
  return api.get<Establishment>('/estabelecimentos/me').then((response) => response.data)
}

export function updateMyEstablishment(input: EstablishmentUpdateInput) {
  return api.put<Establishment>('/estabelecimentos/me', input).then((response) => response.data)
}

export function blockMyEstablishmentBookings() {
  return api.patch<Establishment>('/estabelecimentos/me/bloqueio-agendamentos').then((response) => response.data)
}

export function allowMyEstablishmentBookings() {
  return api.delete<Establishment>('/estabelecimentos/me/bloqueio-agendamentos').then((response) => response.data)
}

export function pauseMyEstablishment() {
  return api.patch<Establishment>('/estabelecimentos/me/pausa').then((response) => response.data)
}

export function resumeMyEstablishment() {
  return api.delete<Establishment>('/estabelecimentos/me/pausa').then((response) => response.data)
}
