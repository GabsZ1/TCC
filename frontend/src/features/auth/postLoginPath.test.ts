/** Protege por testes o comportamento de postLoginPath, incluindo os cenários de regressão deste módulo. */
import { describe, expect, it } from 'vitest'
import { postLoginPath } from './postLoginPath'

describe('postLoginPath', () => {
  it('retorna a rota protegida solicitada para o perfil correto', () => {
    expect(postLoginPath('ADMIN', '/relatorio-financeiro')).toBe('/relatorio-financeiro')
    expect(postLoginPath('ADMIN', '/funcionarios/42/agenda')).toBe('/funcionarios/42/agenda')
    expect(postLoginPath('PROFESSIONAL', '/clientes')).toBe('/clientes')
    expect(postLoginPath('CLIENT', '/estabelecimentos/12/agendar')).toBe('/estabelecimentos/12/agendar')
  })

  it('não redireciona para uma rota de outro perfil ou URL inválida', () => {
    expect(postLoginPath('CLIENT', '/relatorio-financeiro')).toBe('/')
    expect(postLoginPath('PROFESSIONAL', '/funcionarios/42/agenda')).toBe('/')
    expect(postLoginPath('ADMIN', '//example.com')).toBe('/')
    expect(postLoginPath('CLIENT', '/estabelecimentos/abc/agendar')).toBe('/')
  })
})
