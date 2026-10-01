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
  hasPhoto: boolean
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
  clientId: number | null
  clientName: string
  customerId: number
  startsAt: string
  endsAt: string
  status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'INVALIDATED' | 'NO_SHOW'
  invalidationReason: string | null
  completedAt: string | null
  cancellationReason: string | null
  cancelledAt: string | null
  cancelledByUserId: number | null
  cancelledByUserName: string | null
}

export interface ActiveEstablishment {
  id: number
  name: string
  minimumAdvanceHours: number
  maximumAdvanceDays: number
  hasLogo: boolean
  distanceKm?: number | null
}

export interface PublicEstablishmentProfile {
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
  latitude: number | null
  longitude: number | null
  hasLogo: boolean
  hasCover: boolean
  businessHours: Array<{ dayOfWeek: string; closed: boolean; openingTime: string | null; closingTime: string | null }>
  professionals: CatalogProfessional[]
  services: CatalogService[]
}

export function fetchActiveEstablishments() {
  return api.get<ActiveEstablishment[]>('/estabelecimentos').then((response) => response.data)
}

export function fetchNearbyEstablishments(latitude: number, longitude: number) {
  return api.post<ActiveEstablishment[]>('/estabelecimentos/proximos', { latitude, longitude }).then((response) => response.data)
}

export function fetchEstablishmentProfile(establishmentId: number) {
  return api.get<PublicEstablishmentProfile>(`/estabelecimentos/${establishmentId}/perfil`).then((response) => response.data)
}

export function fetchCatalogServices(establishmentId: number) {
  return api.get<CatalogService[]>(`/estabelecimentos/${establishmentId}/catalogo/servicos`).then((response) => response.data)
}

export function fetchCatalogProfessionals(establishmentId: number, serviceId?: number | null) {
  return api.get<CatalogProfessional[]>(`/estabelecimentos/${establishmentId}/catalogo/funcionarios`, { params: { serviceId } }).then((response) => response.data)
}

/** Foto do profissional exibida ao cliente (carregada com o token, por isso vem como blob). */
export function fetchCatalogProfessionalPhoto(establishmentId: number, professionalId: number) {
  return api.get<Blob>(`/estabelecimentos/${establishmentId}/catalogo/funcionarios/${professionalId}/foto`, { responseType: 'blob' }).then((response) => response.data)
}

export function fetchAvailability(establishmentId: number, serviceId: number, professionalId: number, date: string, appointmentId?: number) {
  return api.get<Availability>(`/estabelecimentos/${establishmentId}/agendamentos/disponibilidade`, { params: { serviceId, professionalId, date, appointmentId } }).then((response) => response.data)
}

export function createAppointment(establishmentId: number, serviceId: number, professionalId: number, startsAt: string) {
  return api.post<Appointment>(`/estabelecimentos/${establishmentId}/agendamentos`, { serviceId, professionalId, startsAt }).then((response) => response.data)
}

export function fetchMyAppointments(page = 0) {
  return api.get<{ content: Appointment[]; totalElements: number; totalPages: number }>(
    '/agendamentos/me', { params: { page, size: 20 } }).then((response) => response.data)
}
export function fetchMyAppointmentPage(history: boolean, page: number) {
  return api.get<{ content: Appointment[]; number: number; totalPages: number; totalElements: number }>(
    '/agendamentos/me/pagina', { params: { history, page, size: 10 } }).then((response) => response.data)
}
export function fetchNextClientAppointment() {
  return api.get<Appointment | null>('/agendamentos/me/proximo').then((response) => response.data || null)
}
export function fetchProfessionalToday() {
  return api.get<{ confirmedCount: number; next: Appointment | null }>('/agendamentos/profissional/me/hoje').then((response) => response.data)
}

export function cancelAppointment(appointmentId: number) {
  return api.patch<Appointment>(`/agendamentos/${appointmentId}/cancelamento`).then((response) => response.data)
}

export function cancelAppointmentAsAdministrator(appointmentId: number, reason: string) {
  return api.patch<Appointment>(`/agendamentos/${appointmentId}/cancelamento-administrativo`, { reason }).then((response) => response.data)
}

export function completeAppointment(appointmentId: number) {
  return api.patch<Appointment>(`/agendamentos/${appointmentId}/conclusao`).then((response) => response.data)
}

export const PENDING_CLOSURE_KEY = ['pending-closure']

/** Atendimentos que já terminaram e continuam confirmados (admin: do estabelecimento; profissional: os dele). */
export function fetchPendingClosure(page = 0) {
  return api.get<{ content: Appointment[]; totalElements: number; totalPages: number }>('/agendamentos/pendentes-fechamento', { params: { page, size: 10 } }).then((response) => response.data)
}

/** Remarcação pelo admin: muda horário e/ou profissional (e, opcionalmente, o serviço). */
export function rescheduleAppointment(appointmentId: number, input: { serviceId: number; professionalId: number; startsAt: string }) {
  return api.put<Appointment>(`/agendamentos/${appointmentId}/remarcacao`, input).then((response) => response.data)
}

export function markAppointmentNoShow(appointmentId: number) {
  return api.patch<Appointment>(`/agendamentos/${appointmentId}/falta`).then((response) => response.data)
}

export function invalidateAppointment(appointmentId: number, reason: string) {
  return api.patch<Appointment>(`/agendamentos/${appointmentId}/invalidacao`, { reason }).then((response) => response.data)
}

export function updateAppointment(appointmentId: number, serviceId: number, professionalId: number, startsAt: string) {
  return api.put<Appointment>(`/agendamentos/${appointmentId}`, { serviceId, professionalId, startsAt }).then((response) => response.data)
}

export function fetchMyAppointmentsAsProfessional() {
  return api.get<{ content: Appointment[]; totalElements: number; totalPages: number }>('/agendamentos/profissional/me').then((response) => response.data)
}

export interface EstablishmentAgendaFilters {
  date?: string
  professionalId?: number
  status?: Appointment['status']
  page?: number
}

export interface TeamAvailability {
  professionalId: number
  professionalName: string
  working: boolean
  openingTime: string | null
  closingTime: string | null
  blocks: Array<{ id: number; startsAt: string; endsAt: string; allDay: boolean }>
}

export function fetchEstablishmentAgenda(filters: EstablishmentAgendaFilters) {
  return api.get<{ content: Appointment[]; totalElements: number; totalPages: number }>('/agendamentos/estabelecimento/me', { params: { ...filters, size: 20 } }).then((response) => response.data)
}

export function fetchTeamAvailability(date: string, professionalId?: number) {
  return api.get<TeamAvailability[]>('/agendamentos/estabelecimento/me/disponibilidade-equipe', { params: { date, professionalId } }).then((response) => response.data)
}
