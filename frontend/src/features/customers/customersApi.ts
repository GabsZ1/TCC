import { api } from '@/lib/api'
import type { Appointment } from '@/features/appointments/appointmentsApi'

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
export const createCustomer = (input: CustomerInput) =>
  api.post<Customer>('/clientes/estabelecimento/me', input).then((response) => response.data)
export const updateCustomer = (id: number, input: CustomerInput) =>
  api.put<Customer>(`/clientes/estabelecimento/me/${id}`, input).then((response) => response.data)
export const fetchCustomerAppointments = (id: number) =>
  api.get<Appointment[]>(`/clientes/estabelecimento/me/${id}/agendamentos`).then((response) => response.data)
export const fetchBookingOptions = () =>
  api.get<{ services: Array<{ id: number; name: string }>; professionals: Array<{ id: number; name: string }> }>('/clientes/estabelecimento/me/opcoes-agendamento').then((response) => response.data)
export const createCustomerAppointment = (customerId: number, serviceId: number, professionalId: number, startsAt: string) =>
  api.post<Appointment>(`/agendamentos/estabelecimento/me/clientes/${customerId}`, { serviceId, professionalId, startsAt })
    .then((response) => response.data)
