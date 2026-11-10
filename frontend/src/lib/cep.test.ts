/** Protege por testes o comportamento de cep, incluindo os cenários de regressão deste módulo. */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CepNotFoundError, formatCep, lookupCep } from './cep'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('cep', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('formata o CEP para exibição', () => {
    expect(formatCep('87010')).toBe('87010')
    expect(formatCep('87010-000')).toBe('87010-000')
    expect(formatCep('870100001234')).toBe('87010-000')
  })

  it('preenche o endereço pela ViaCEP', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ logradouro: 'Avenida Brasil', bairro: 'Centro', localidade: 'Maringá', uf: 'PR' }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(lookupCep('87013-000')).resolves.toEqual({ street: 'Avenida Brasil', neighborhood: 'Centro', city: 'Maringá', state: 'PR' })
    expect(fetchMock).toHaveBeenCalledWith('https://viacep.com.br/ws/87013000/json/', expect.anything())
  })

  it('informa CEP inexistente', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ erro: 'true' })))
    await expect(lookupCep('99999999')).rejects.toBeInstanceOf(CepNotFoundError)
  })

  it('usa a BrasilAPI quando a ViaCEP falha', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse({ street: 'Rua A', neighborhood: 'Zona 7', city: 'Maringá', state: 'PR' }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(lookupCep('87020000')).resolves.toEqual({ street: 'Rua A', neighborhood: 'Zona 7', city: 'Maringá', state: 'PR' })
    expect(fetchMock).toHaveBeenLastCalledWith('https://brasilapi.com.br/api/cep/v1/87020000', expect.anything())
  })
})
