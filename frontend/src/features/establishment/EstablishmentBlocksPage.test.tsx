/** Protege por testes o comportamento de EstablishmentBlocksPage, incluindo os cenários de regressão deste módulo. */
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { EstablishmentBlocksPage } from './EstablishmentBlocksPage'
import { blocksQueryRange } from './establishmentBlocksUtils'

describe('exceções do estabelecimento', () => {
  afterEach(() => vi.useRealTimers())

  it('mostra período, motivo e ações do bloqueio retornado', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-30T12:00:00'))
    const client = new QueryClient()
    client.setQueryData(['establishment-blocks', '2026-09-01', '2026-10-01'], [
      { id: 7, startsAt: '2026-10-01T14:00:00', endsAt: '2026-10-01T18:00:00', reason: 'Manutenção' },
    ])
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(EstablishmentBlocksPage)))
    expect(html).toContain('Manutenção')
    expect(html).toContain('Editar')
    expect(html).toContain('Remover')
    expect(html).toContain('datetime-local')
  })

  it.each([
    ['2026-11-22', '2026-11-01', '2026-12-01'],
    ['2026-12-22', '2026-12-01', '2027-01-01'],
  ])('abre o filtro de %s com a próxima virada de mês', (today, start, end) => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(`${today}T12:00:00-03:00`))
    const client = new QueryClient()
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(EstablishmentBlocksPage)))
    expect(html).toContain(`type="date" value="${start}"`)
    expect(html).toContain(`type="date" value="${end}"`)
  })

  it('uses the day after the selected end date as the exclusive API limit', () => {
    expect(blocksQueryRange('2026-10-15', '2026-10-15')).toEqual({ startsAt: '2026-10-15T00:00', endsAt: '2026-10-16T00:00' })
    expect(blocksQueryRange('2026-10-10', '2026-10-15').endsAt).toBe('2026-10-16T00:00')
  })
})
