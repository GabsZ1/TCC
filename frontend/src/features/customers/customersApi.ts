import { api } from '@/lib/api'
import type { Appointment } from '@/features/appointments/appointmentsApi'
import { phoneDigits } from '@/lib/phone'

export interface Customer {
  id: number
  establishmentId: number
  userId: number | null
  name: string
  phone: string
  email: string | null
  active: boolean
}

export interface CustomerInput { name: string; phone: string; email: string; active?: boolean }

export const fetchCustomers = (search = '') =>
  api.get<Customer[]>('/clientes/estabelecimento/me', { params: { search } }).then((response) => response.data)
export const fetchCustomerSuggestions = (phone: string, email: string) =>
  api.get<Customer[]>('/clientes/estabelecimento/me/sugestoes', { params: { phone: phoneDigits(phone), email } }).then((response) => response.data)
export const createCustomer = (input: CustomerInput) =>
  api.post<Customer>('/clientes/estabelecimento/me', { ...input, phone: phoneDigits(input.phone) }).then((response) => response.data)
export const updateCustomer = (id: number, input: CustomerInput) =>
  api.put<Customer>(`/clientes/estabelecimento/me/${id}`, { ...input, phone: phoneDigits(input.phone) }).then((response) => response.data)
export interface CustomerDuplicateCandidate { id: number; name: string; phone: string | null; email: string | null; linked: boolean; appointmentCount: number }
export const fetchCustomerDuplicates = (id: number) =>
  api.get<CustomerDuplicateCandidate[]>(`/clientes/estabelecimento/me/${id}/possiveis-duplicados`).then((response) => response.data)
export const mergeCustomers = (id: number, otherId: number) =>
  api.patch<Customer>(`/clientes/estabelecimento/me/${id}/mesclar/${otherId}`).then((response) => response.data)
export const keepCustomersSeparate = (id: number, otherId: number) =>
  api.patch(`/clientes/estabelecimento/me/${id}/manter-separados/${otherId}`)
export const fetchCustomerAppointments = (id: number) =>
  api.get<Appointment[]>(`/clientes/estabelecimento/me/${id}/agendamentos`).then((response) => response.data)
export const fetchBookingOptions = (serviceId?: number) =>
  api.get<{ services: Array<{ id: number; name: string }>; professionals: Array<{ id: number; name: string }> }>('/clientes/estabelecimento/me/opcoes-agendamento', { params: { serviceId } }).then((response) => response.data)
export const fetchInternalAvailability = (customerId: number, serviceId: number, professionalId: number, date: string, appointmentId?: number) =>
  api.get<{ date: string; durationMinutes: number; availableTimes: string[] }>(`/agendamentos/estabelecimento/me/clientes/${customerId}/disponibilidade`, { params: { serviceId, professionalId, date, appointmentId } }).then((response) => response.data)
export const createCustomerAppointment = (customerId: number, serviceId: number, professionalId: number, startsAt: string) =>
  api.post<Appointment>(`/agendamentos/estabelecimento/me/clientes/${customerId}`, { serviceId, professionalId, startsAt })
    .then((response) => response.data)
