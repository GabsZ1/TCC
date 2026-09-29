import { api } from '@/lib/api'

export interface SystemEstablishment {
  id: number
  name: string
  cnpj: string
  email: string | null
  active: boolean
  paused: boolean
  pausedBySystem: boolean
  bookingsBlocked: boolean
  bookingsBlockedBySystem: boolean
  createdAt: string
}

export interface SystemEstablishmentDetails {
  establishment: SystemEstablishment
  minimumAdvanceMinutes: number
  maximumAdvanceDays: number
  administrators: Array<{ id: number; name: string; email: string; active: boolean }>
  profile: {
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
  } | null
  businessHours: Array<{ dayOfWeek: string; closed: boolean; openingTime: string | null; closingTime: string | null }>
  stats: {
    activeProfessionals: number
    totalProfessionals: number
    activeServices: number
    totalServices: number
    upcomingAppointments: number
    completedThisMonth: number
    totalAppointments: number
  }
  professionals: Array<{ id: number; name: string; email: string; occupation: string | null; active: boolean }>
  services: Array<{ id: number; name: string; price: number; durationMinutes: number; active: boolean }>
}

export function fetchSystemEstablishmentDetails(establishmentId: number) {
  return api.get<SystemEstablishmentDetails>(`/admin-sistema/estabelecimentos/${establishmentId}`).then((response) => response.data)
}

export function fetchSystemEstablishments() {
  return api.get<SystemEstablishment[]>('/admin-sistema/estabelecimentos').then((response) => response.data)
}

export function deactivateSystemEstablishment(establishmentId: number) {
  return api.delete(`/admin-sistema/estabelecimentos/${establishmentId}`)
}

export function reactivateSystemEstablishment(establishmentId: number) {
  return api.patch(`/admin-sistema/estabelecimentos/${establishmentId}/ativacao`)
}

export function blockSystemEstablishmentBookings(establishmentId: number) {
  return api.patch(`/admin-sistema/estabelecimentos/${establishmentId}/bloqueio-agendamentos`)
}

export function allowSystemEstablishmentBookings(establishmentId: number) {
  return api.delete(`/admin-sistema/estabelecimentos/${establishmentId}/bloqueio-agendamentos`)
}

export function pauseSystemEstablishment(establishmentId: number) {
  return api.patch(`/admin-sistema/estabelecimentos/${establishmentId}/pausa`)
}

export function resumeSystemEstablishment(establishmentId: number) {
  return api.delete(`/admin-sistema/estabelecimentos/${establishmentId}/pausa`)
}
