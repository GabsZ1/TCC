import { afterEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { api } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('rotas públicas de autenticação', () => {
  it('não envia o token salvo nem apaga a sessão após um 401 público', async () => {
    const storage = new Map([['salonhub:token', 'token-atual']])
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    })

    await expect(api.get('/auth/redefinir-senha', {
      adapter: (config) => {
        expect(config.headers.Authorization).toBeUndefined()
        const response = { data: null, status: 401, statusText: 'Unauthorized', headers: {}, config }
        return Promise.reject(new AxiosError('Unauthorized', 'ERR_BAD_RESPONSE', config, undefined, response))
      },
    })).rejects.toMatchObject({ response: { status: 401 } })

    expect(storage.get('salonhub:token')).toBe('token-atual')
  })
})
