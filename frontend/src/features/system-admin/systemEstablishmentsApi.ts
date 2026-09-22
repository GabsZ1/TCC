import { api } from '@/lib/api'

export interface SystemEstablishment {
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
  createdAt: string
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
