/** Centraliza as operações HTTP de funcionários e de seus serviços. */
import { api } from '@/lib/api'
import type { UserRole } from '@/features/auth/session/types'
import type { SalonService } from '@/features/catalog/api/servicesApi'

export interface Employee {
  id: number
  name: string
  email: string
  role: UserRole
  establishmentId: number | null
  establishmentName: string | null
  active: boolean
  passwordInitialized: boolean
  occupation: string | null
  cpf: string | null
  phone: string | null
  instagram: string | null
  address: string | null
  hireDate: string | null
  hasPhoto: boolean
}

/** Cadastro feito pelo administrador (US 2.1). CPF e data de admissão travam depois de gravados. */
export interface EmployeeCreateInput {
  name: string
  email: string
  occupation: string
  cpf: string
  hireDate: string
}

/** Edição pelo administrador: nome, ocupação e, só se estiverem vazios, CPF e data de admissão. */
export interface EmployeeUpdateInput {
  name: string
  occupation: string
  cpf?: string
  hireDate?: string
}

/** Perfil do próprio funcionário. Trocar o e-mail exige a senha atual. */
export interface EmployeeSelfProfileInput {
  email: string
  phone?: string
  instagram?: string
  address?: string
  currentPassword?: string
}

export interface EmployeeCreationResult {
  id: number
  name: string
  email: string
  active: boolean
}

export interface EmployeeRemovalResult {
  removedPermanently: boolean
  message: string
}

export interface ProfessionalServices {
  professionalId: number
  allServices: boolean
  services: SalonService[]
}

export function fetchEmployees() {
  return api.get<Employee[]>('/funcionarios').then((res) => res.data)
}

export function createEmployee(input: EmployeeCreateInput) {
  return api.post<EmployeeCreationResult>('/funcionarios', input).then((res) => res.data)
}

export function resendEmployeeInvitation(id: number) {
  return api.post<EmployeeCreationResult>(`/funcionarios/${id}/reenviar-convite`).then((res) => res.data)
}

export function updateEmployee(id: number, input: EmployeeUpdateInput) {
  return api.put<Employee>(`/funcionarios/${id}`, input).then((res) => res.data)
}

export function deactivateEmployee(id: number) {
  return api.patch<Employee>(`/funcionarios/${id}/desativacao`).then((res) => res.data)
}

export function reactivateEmployee(id: number) {
  return api.patch<Employee>(`/funcionarios/${id}/ativacao`).then((res) => res.data)
}

export function fetchEmployeeServices(id: number) {
  return api.get<ProfessionalServices>(`/funcionarios/${id}/servicos`).then((res) => res.data)
}

export function updateEmployeeServices(id: number, allServices: boolean, serviceIds: number[]) {
  return api.put<ProfessionalServices>(`/funcionarios/${id}/servicos`, { allServices, serviceIds }).then((res) => res.data)
}

export function fetchMyProfessionalServices() {
  return api.get<ProfessionalServices>('/funcionarios/me/servicos').then((res) => res.data)
}

/** Exclui definitivamente; o backend rejeita a operação se houver vínculos. */
export function removeEmployee(id: number) {
  return api.delete<EmployeeRemovalResult>(`/funcionarios/${id}`).then((res) => res.data)
}

export function fetchMyEmployeeProfile() {
  return api.get<Employee>('/funcionarios/me/perfil').then((res) => res.data)
}

export function updateMyEmployeeProfile(input: EmployeeSelfProfileInput) {
  return api.put<Employee>('/funcionarios/me/perfil', input).then((res) => res.data)
}

/** Foto: só o próprio funcionário envia ou remove (JPG, PNG ou WEBP de até 2 MB). */
export function uploadMyPhoto(file: File) {
  const form = new FormData()
  form.append('file', file)
  return api.put<void>('/funcionarios/me/foto', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function deleteMyPhoto() {
  return api.delete<void>('/funcionarios/me/foto')
}

/** Administrador ou o próprio funcionário podem ver a foto. */
export function fetchEmployeePhoto(id: number) {
  return api.get<Blob>(`/funcionarios/${id}/foto`, { responseType: 'blob' }).then((res) => res.data)
}
