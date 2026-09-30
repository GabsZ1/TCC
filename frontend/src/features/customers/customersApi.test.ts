import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/lib/api'
import { fetchCustomerAppointments, fetchCustomers } from './customersApi'

vi.mock('@/lib/api', () => ({ api: { get: vi.fn() } }))

describe('listagens de clientes', () => {
  beforeEach(() => vi.mocked(api.get).mockReset())

  it('envia a página, busca e status ao backend', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { content: [], number: 2, totalPages: 4, totalElements: 75 } })
    const result = await fetchCustomers('maria@example.com', 2, false)
    expect(api.get).toHaveBeenCalledWith('/clientes/estabelecimento/me', {
      params: { search: 'maria@example.com', page: 2, size: 20, active: false },
    })
    expect(result.totalElements).toBe(75)
  })

  it('busca só a página solicitada do histórico', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { content: [], number: 1, totalPages: 2, totalElements: 15 } })
    await fetchCustomerAppointments(42, 1)
    expect(api.get).toHaveBeenCalledWith('/clientes/estabelecimento/me/42/agendamentos', {
      params: { page: 1, size: 10 },
    })
  })
})
