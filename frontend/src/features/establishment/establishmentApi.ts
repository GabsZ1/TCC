import { api } from '@/lib/api'

export interface Establishment {
  id: number
  name: string
  cnpj: string
  email: string | null
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
  businessHours: BusinessHour[]
  minimumAdvanceHours: number
  maximumAdvanceDays: number
}

export interface EstablishmentProfile {
  establishmentId: number
  establishmentName: string
  aboutUs: string | null
  instagram: string | null
  whatsapp: string | null
  phone: string | null
  postalCode: string | null
  street: string | null
  streetNumber: string | null
  addressComplement: string | null
  neighborhood: string | null
  city: string | null
  state: string | null
  locationAvailable: boolean
  businessHours: BusinessHour[]
  professionals: Array<{ id: number; name: string }>
  services: Array<{ id: number; name: string; description: string | null; price: number; durationMinutes: number }>
}

export type EstablishmentProfileInput = Pick<EstablishmentProfile,
  'aboutUs' | 'instagram' | 'whatsapp' | 'phone' | 'postalCode' | 'street' |
  'streetNumber' | 'addressComplement' | 'neighborhood' | 'city' | 'state'>

export function fetchMyEstablishment() {
  return api.get<Establishment>('/estabelecimentos/me').then((response) => response.data)
}

export function updateMyEstablishment(input: EstablishmentUpdateInput) {
  return api.put<Establishment>('/estabelecimentos/me', input).then((response) => response.data)
}

export function fetchMyEstablishmentProfile() {
  return api.get<EstablishmentProfile>('/estabelecimentos/me/perfil').then((response) => response.data)
}

export function updateMyEstablishmentProfile(input: EstablishmentProfileInput) {
  return api.put<EstablishmentProfile>('/estabelecimentos/me/perfil', input).then((response) => response.data)
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
