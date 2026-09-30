import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { CustomersPage } from './CustomersPage'

vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: { role: 'ADMIN', id: 1 } }) }))

describe('lista de clientes paginada', () => {
  it('exibe apenas o conteúdo recebido para a página atual e os controles de navegação', () => {
    const client = new QueryClient()
    client.setQueryData(['customers', '', 0, 'all'], {
      content: [{ id: 1, establishmentId: 1, userId: null, name: 'Ana', phone: '44999990001', email: null, active: true }],
      number: 0, totalPages: 3, totalElements: 41,
    })
    client.setQueryData(['customer-booking-options', ''], { services: [], professionals: [] })
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(CustomersPage)))
    expect(html).toContain('Ana')
    expect(html).toContain('Página 1 de 3')
    expect(html).toContain('Próxima')
  })
})
