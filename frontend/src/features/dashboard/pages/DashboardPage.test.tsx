import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'

vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: { role: 'ADMIN', name: 'Ana', establishmentName: 'Studio Ana' } }) }))

describe('dashboard administrativo', () => {
  it('mostra a operação do dia e só os próximos atendimentos retornados pela API', () => {
    const client = new QueryClient()
    client.setQueryData(['dashboard-summary'], {
      todaysCompletedServices: 4, monthlyCommissionTotal: 50, activeEmployees: 2,
      todaysAppointments: 12, pendingClosure: 1, todaysRevenue: 620,
      upcomingAppointments: [{ id: 1, startsAt: '2026-09-30T09:30:00', customerName: 'Maria', serviceName: 'Corte', professionalName: 'Ana' }],
    })
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client },
      createElement(MemoryRouter, null, createElement(DashboardPage))))
    expect(html).toContain('Agendamentos hoje')
    expect(html).toContain('Pendentes de fechamento')
    expect(html).toContain('R$ 620,00')
    expect(html).toContain('Maria')
    expect(html).not.toContain('Comissões no mês')
  })
})
