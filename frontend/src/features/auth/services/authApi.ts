import { api } from '@/lib/api'
import type { AuthenticatedUser } from '@/types/auth'

export interface InitialRegistrationInput {
  ownerName: string
  ownerEmail: string
  password: string
  establishmentName: string
  cnpj: string
  establishmentEmail?: string
  establishmentPhone?: string
}

export interface InitialRegistrationResponse {
  establishmentId: number
  establishmentName: string
  userId: number
  ownerName: string
  ownerEmail: string
}

export interface ClientRegistrationInput {
  name: string
  email: string
  password: string
}

export function registerInitial(input: InitialRegistrationInput) {
  return api.post<InitialRegistrationResponse>('/auth/cadastro', input).then((response) => response.data)
}

export function registerClient(input: ClientRegistrationInput) {
  return api.post<AuthenticatedUser>('/auth/clientes', input).then((response) => response.data)
}

export function completeFirstAccess(token: string, password: string) {
  return api.post<void>('/auth/primeiro-acesso', { token, password })
}

export interface PasswordChangeInput {
  currentPassword: string
  newPassword: string
  newPasswordConfirmation: string
}

export function changePassword(input: PasswordChangeInput) {
  return api.patch<void>('/auth/senha', input)
}
