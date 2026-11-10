/** Protege por testes o comportamento de formatCnpj, incluindo os cenários de regressão deste módulo. */
import { describe, expect, it } from 'vitest'
import { formatCnpj } from './format'

describe('formatCnpj', () => {
  it('formata CNPJ numérico e alfanumérico sem remover letras', () => {
    expect(formatCnpj('11222333000181')).toBe('11.222.333/0001-81')
    expect(formatCnpj('12ABC34501DE35')).toBe('12.ABC.345/01DE-35')
  })
})
