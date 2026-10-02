import type { UserRole } from '@/types/auth'

const exactPathsByRole: Record<UserRole, string[]> = {
  SYSTEM_ADMIN: ['/', '/alterar-senha', '/admin/estabelecimentos'],
  ADMIN: [
    '/', '/alterar-senha', '/funcionarios', '/servicos', '/estabelecimento',
    '/estabelecimento/bloqueios', '/perfil-estabelecimento', '/agenda',
    '/relatorio-comissoes', '/relatorio-atendimentos', '/relatorio-financeiro', '/clientes',
  ],
  CLIENT: ['/', '/alterar-senha', '/agendamentos', '/meu-perfil'],
  PROFESSIONAL: ['/', '/alterar-senha', '/meu-perfil', '/minha-agenda', '/meus-servicos', '/meus-atendimentos', '/clientes'],
}

export function postLoginPath(role: UserRole, requestedPath?: string) {
  if (!requestedPath) return '/'
  if (exactPathsByRole[role].includes(requestedPath)) return requestedPath
  if (role === 'ADMIN' && /^\/funcionarios\/\d+\/agenda$/.test(requestedPath)) return requestedPath
  if (role === 'CLIENT' && /^\/estabelecimentos\/\d+\/agendar$/.test(requestedPath)) return requestedPath
  return '/'
}
