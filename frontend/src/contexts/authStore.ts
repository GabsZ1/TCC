/** Compartilha o estado da sessão com código que executa fora dos componentes React. */
import { createContext } from 'react'
import type { AuthenticatedUser, LoginCredentials } from '@/types/auth'

export interface AuthContextValue {
  user: AuthenticatedUser | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (credentials: LoginCredentials) => Promise<AuthenticatedUser>
  logout: () => void
  /** Recarrega os dados do usuário logado (ex.: depois de editar o perfil). */
  refreshUser: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
