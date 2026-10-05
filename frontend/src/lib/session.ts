/**
 * Regras da sessão no navegador: renovação do token antes de expirar e volta para a
 * página em que o usuário estava quando a sessão termina.
 */

/** Renova o token quando faltar menos que isso para expirar e o usuário estiver ativo. */
export const RENEW_BEFORE_EXPIRY_MS = 30 * 60 * 1000

/** Parâmetro da tela de login que indica sessão expirada ou encerrada. */
export const SESSION_ENDED_PARAM = 'sessao'
export const SESSION_ENDED_VALUE = 'encerrada'
export const REDIRECT_PARAM = 'redirect'

/** Lê o "exp" do JWT (em milissegundos). Só para decidir quando renovar: quem valida é o backend. */
export function tokenExpiresAt(token: string): number | null {
  const payload = token.split('.')[1]
  if (!payload) return null
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const { exp } = JSON.parse(atob(padded)) as { exp?: unknown }
    return typeof exp === 'number' ? exp * 1000 : null
  } catch {
    return null
  }
}

export function shouldRenewToken(token: string, now = Date.now()) {
  const expiresAt = tokenExpiresAt(token)
  return expiresAt !== null && expiresAt > now && expiresAt - now <= RENEW_BEFORE_EXPIRY_MS
}

/** Endereço do login depois que a API recusa a sessão, guardando a página atual. */
export function loginPathAfterSessionEnded(currentPath: string) {
  const params = new URLSearchParams({ [SESSION_ENDED_PARAM]: SESSION_ENDED_VALUE })
  if (currentPath.startsWith('/') && currentPath !== '/' && !currentPath.startsWith('/login')) {
    params.set(REDIRECT_PARAM, currentPath)
  }
  return `/login?${params.toString()}`
}
