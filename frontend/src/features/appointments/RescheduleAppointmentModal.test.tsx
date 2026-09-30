import { describe, expect, it } from 'vitest'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RescheduleAppointmentModal } from './RescheduleAppointmentModal'
import type { Appointment } from './appointmentsApi'

const appointment: Appointment = {
  id: 1, establishmentId: 1, establishmentName: 'Salão', serviceId: 5, serviceName: 'Corte',
  agreedPrice: 50, commissionPercentage: 0, commissionAmount: 0, durationMinutes: 60,
  professionalId: 3, professionalName: 'Ana', clientId: null, clientName: 'Cliente', customerId: 7,
  startsAt: '2026-10-02T10:00:00', endsAt: '2026-10-02T11:00:00', status: 'CONFIRMED',
  invalidationReason: null, completedAt: null, cancellationReason: null, cancelledAt: null,
  cancelledByUserId: null, cancelledByUserName: null,
}

describe('remarcação administrativa', () => {
  it('usa profissionais da opção filtrada pelo serviço e escolhe data e horário separadamente', () => {
    const client = new QueryClient()
    client.setQueryData(['customer-booking-options', '5'], {
      services: [{ id: 5, name: 'Corte' }], professionals: [{ id: 3, name: 'Ana' }],
    })
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client },
      createElement(RescheduleAppointmentModal, { appointment, onRescheduled: () => {}, onClose: () => {} })))
    expect(html).toContain('Ana')
    expect(html).toContain('type="date"')
    expect(html).not.toContain('datetime-local')
  })
})
