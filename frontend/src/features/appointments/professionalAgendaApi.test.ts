import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/lib/api'
import { fetchProfessionalSchedule, updateProfessionalSchedule, createCalendarEvent } from './professionalAgendaApi'

vi.mock('@/lib/api', () => ({ api: { get: vi.fn(), put: vi.fn(), post: vi.fn() } }))

describe('agenda compartilhada entre profissional e administrador', () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset()
    vi.mocked(api.put).mockReset()
    vi.mocked(api.post).mockReset()
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    vi.mocked(api.put).mockResolvedValue({ data: [] })
    vi.mocked(api.post).mockResolvedValue({ data: {} })
  })

  it('usa a rota própria sem identificador e a rota administrativa com identificador', async () => {
    await fetchProfessionalSchedule()
    await fetchProfessionalSchedule(17)
    expect(api.get).toHaveBeenNthCalledWith(1, '/profissionais/me/agenda/horarios')
    expect(api.get).toHaveBeenNthCalledWith(2, '/funcionarios/17/agenda/horarios')
  })

  it('envia alterações para a agenda do funcionário selecionado', async () => {
    await updateProfessionalSchedule([], 17)
    await createCalendarEvent({ title: 'Folga', description: null, startsAt: '2026-10-01T10:00', endsAt: '2026-10-01T11:00', allDay: false }, 17)
    expect(api.put).toHaveBeenCalledWith('/funcionarios/17/agenda/horarios', { businessHours: [] })
    expect(api.post).toHaveBeenCalledWith('/funcionarios/17/agenda/eventos', expect.objectContaining({ title: 'Folga' }))
  })
})
