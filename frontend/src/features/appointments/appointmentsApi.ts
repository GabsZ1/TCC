import { api } from '@/lib/api'

export interface CatalogService {
  id: number
  name: string
  description: string | null
  price: number
  durationMinutes: number
}

export interface CatalogProfessional {
  id: number
  name: string
}

export interface Availability {
  date: string
  durationMinutes: number
  availableTimes: string[]
}

export interface Appointment {
  id: number
  establishmentId: number
  establishmentName: string
  serviceId: number
  serviceName: string
  agreedPrice: number
  commissionPercentage: number
  commissionAmount: number
  durationMinutes: number
  professionalId: number
  professionalName: string
  clientId: number
  clientName: string
  startsAt: string
  endsAt: string
  status: 'CONFIRMED' | 'CANCELLED'
}

export interface ActiveEstablishment {
  id: number
  name: string
}

export function fetchActiveEstablishments() {
  return api.get<ActiveEstablishment[]>('/estabelecimentos').then((response) => response.data)
}

export function fetchCatalogServices(establishmentId: number) {
  return api.get<CatalogService[]>(`/estabelecimentos/${establishmentId}/catalogo/servicos`).then((response) => response.data)
}

export function fetchCatalogProfessionals(establishmentId: number) {
  return api.get<CatalogProfessional[]>(`/estabelecimentos/${establishmentId}/catalogo/funcionarios`).then((response) => response.data)
}

export function fetchAvailability(establishmentId: number, serviceId: number, professionalId: number, date: string, appointmentId?: number) {
  return api.get<Availability>(`/estabelecimentos/${establishmentId}/agendamentos/disponibilidade`, { params: { serviceId, professionalId, date, appointmentId } }).then((response) => response.data)
}

export function createAppointment(establishmentId: number, serviceId: number, professionalId: number, startsAt: string) {
  return api.post<Appointment>(`/estabelecimentos/${establishmentId}/agendamentos`, { serviceId, professionalId, startsAt }).then((response) => response.data)
}

export function fetchMyAppointments() {
  return api.get<Appointment[]>('/agendamentos/me').then((response) => response.data)
}

export function cancelAppointment(appointmentId: number) {
  return api.patch<Appointment>(`/agendamentos/${appointmentId}/cancelamento`).then((response) => response.data)
}

export function updateAppointment(appointmentId: number, serviceId: number, professionalId: number, startsAt: string) {
  return api.put<Appointment>(`/agendamentos/${appointmentId}`, { serviceId, professionalId, startsAt }).then((response) => response.data)
}

export function fetchMyAppointmentsAsProfessional() {
  return api.get<Appointment[]>('/agendamentos/profissional/me').then((response) => response.data)
}
