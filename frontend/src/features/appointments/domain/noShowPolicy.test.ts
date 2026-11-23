/** Protege por testes o comportamento de noShowPolicy, incluindo os cenários de regressão deste módulo. */
import { describe, expect, it } from 'vitest'
import { canMarkNoShow } from './noShowPolicy'

describe('canMarkNoShow', () => {
  it('libera a falta só depois de 15 minutos do início', () => {
    expect(canMarkNoShow('2026-09-29T14:00:00', '2026-09-29T14:14:59')).toBe(false)
    expect(canMarkNoShow('2026-09-29T14:00:00', '2026-09-29T14:15:00')).toBe(true)
    expect(canMarkNoShow('2026-09-29T14:00:00', '2026-09-30T09:00:00')).toBe(true)
  })
})
