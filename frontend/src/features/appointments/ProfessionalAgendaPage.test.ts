import { describe, expect, it } from 'vitest'
import { filterAgendaItems, findAgendaConflict, normalizeEventInput, rangeAfterStartChange } from './professionalAgendaUtils'
import type { ProfessionalAgendaItem } from './professionalAgendaApi'

function item(overrides: Partial<ProfessionalAgendaItem> = {}): ProfessionalAgendaItem {
  return {
    type: 'APPOINTMENT', id: 1, title: 'Corte', description: null,
    startsAt: '2026-09-22T10:00:00', endsAt: '2026-09-22T11:00:00', allDay: false,
    status: 'CONFIRMED', clientName: 'Ana', clientEmail: 'ana@example.com', serviceName: 'Corte',
    durationMinutes: 60, agreedPrice: 50, cancellationReason: null, invalidationReason: null,
    ...overrides,
  }
}

describe('agenda do profissional', () => {
  it('normaliza compromisso de dia inteiro para um intervalo exclusivo de pelo menos um dia', () => {
    expect(normalizeEventInput({ title: 'Folga', description: null, startsAt: '2026-09-22T00:00', endsAt: '2026-09-22T00:00', allDay: true }))
      .toMatchObject({ startsAt: '2026-09-22T00:00:00', endsAt: '2026-09-23T00:00:00' })
  })

  it('preserva uma hora de duração ao mover o início para depois do término atual', () => {
    expect(rangeAfterStartChange('2026-09-22T10:00', '2026-09-22T11:00', '2026-09-22T12:00', false))
      .toEqual({ startsAt: '2026-09-22T12:00', endsAt: '2026-09-22T13:00' })
  })

  it('detecta conflito apenas com atendimentos confirmados e compromissos pessoais', () => {
    const cancelled = item({ status: 'CANCELLED' })
    expect(findAgendaConflict([cancelled], '2026-09-22T10:30', '2026-09-22T10:45')).toBeNull()
    expect(findAgendaConflict([item()], '2026-09-22T10:30', '2026-09-22T10:45')?.title).toBe('Corte')
  })

  it('filtra ativos e encerrados sem esconder compromissos pessoais ativos', () => {
    const event = item({ type: 'EVENT', id: 2, title: 'Médico', status: null, clientName: null, clientEmail: null, serviceName: null, durationMinutes: null, agreedPrice: null })
    const completed = item({ id: 3, status: 'COMPLETED' })
    expect(filterAgendaItems([item(), event, completed], 'active').map((entry) => entry.id)).toEqual([1, 2])
    expect(filterAgendaItems([item(), event, completed], 'finished').map((entry) => entry.id)).toEqual([3])
  })
})
