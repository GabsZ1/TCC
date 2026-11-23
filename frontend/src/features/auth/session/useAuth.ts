/** Expõe o contexto de autenticação aos componentes com uma interface única. */
import { useContext } from 'react'
import { AuthContext } from '@/features/auth/session/authStore'

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth deve ser usado dentro de um <AuthProvider>')
  }
  return context
}
