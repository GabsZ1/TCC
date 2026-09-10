export type UserRole = 'ADMIN' | 'PROFESSIONAL' | 'CLIENT'

export interface AuthenticatedUser {
  id: number
  name: string
  email: string
  role: UserRole
  establishmentId: number | null
  establishmentName: string | null
  active: boolean
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  type: string
  expiresAt: string
  user: AuthenticatedUser
}
