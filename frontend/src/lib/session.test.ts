/** Protege por testes o comportamento de session, incluindo os cenários de regressão deste módulo. */
import { describe, expect, it } from 'vitest'
import { loginPathAfterSessionEnded, shouldRenewToken, tokenExpiresAt } from './session'

function tokenExpiringAt(expMs: number) {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `${encode({ alg: 'HS256' })}.${encode({ sub: '1', exp: Math.floor(expMs / 1000) })}.assinatura`
}

describe('sessão', () => {
  const now = Date.UTC(2026, 9, 5, 12, 0, 0)

  it('lê a expiração do token', () => {
    expect(tokenExpiresAt(tokenExpiringAt(now + 60_000))).toBe(now + 60_000)
    expect(tokenExpiresAt('token-invalido')).toBeNull()
    expect(tokenExpiresAt('a.%%%.c')).toBeNull()
  })

  it('renova só quando faltam 30 minutos ou menos e o token ainda vale', () => {
    expect(shouldRenewToken(tokenExpiringAt(now + 90 * 60_000), now)).toBe(false)
    expect(shouldRenewToken(tokenExpiringAt(now + 30 * 60_000), now)).toBe(true)
    expect(shouldRenewToken(tokenExpiringAt(now + 60_000), now)).toBe(true)
    expect(shouldRenewToken(tokenExpiringAt(now - 1_000), now)).toBe(false)
  })

  it('volta para a página em que o usuário estava', () => {
    expect(loginPathAfterSessionEnded('/agenda')).toBe('/login?sessao=encerrada&redirect=%2Fagenda')
    expect(loginPathAfterSessionEnded('/')).toBe('/login?sessao=encerrada')
    expect(loginPathAfterSessionEnded('/login')).toBe('/login?sessao=encerrada')
    expect(loginPathAfterSessionEnded('https://outro.site')).toBe('/login?sessao=encerrada')
  })
})
