/** Permite ao cliente consultar e atualizar os próprios dados. */
import { ClientProfilePage } from '@/features/client/pages/ClientProfilePage'
import { ProfessionalProfilePage } from '@/features/employees/pages/ProfessionalProfilePage'
import { useAuth } from '@/features/auth/session/useAuth'

/** "/meu-perfil" mostra o perfil adequado ao papel do usuário (cliente ou profissional). */
export function MyProfilePage() {
  const { user } = useAuth()
  return user?.role === 'PROFESSIONAL' ? <ProfessionalProfilePage /> : <ClientProfilePage />
}
