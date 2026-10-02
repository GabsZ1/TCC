import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/lib/api'
import { uploadEstablishmentImage } from './establishmentImagesApi'
import { uploadGalleryPhoto } from './establishmentGalleryApi'

const originalAdapter = api.defaults.adapter

afterEach(() => {
  api.defaults.adapter = originalAdapter
  vi.unstubAllGlobals()
})

describe('envio de fotos do estabelecimento', () => {
  it('mantém os arquivos como multipart para logo, capa e galeria', async () => {
    vi.stubGlobal('localStorage', { getItem: () => null })
    const requests: Array<{ contentType: string | undefined; data: unknown }> = []
    api.defaults.adapter = async (config) => {
      requests.push({ contentType: config.headers.get('Content-Type')?.toString(), data: config.data })
      return { data: { id: 1, position: 0 }, status: 200, statusText: 'OK', headers: {}, config }
    }
    const file = new File(['conteúdo'], 'ambiente.png', { type: 'image/png' })

    await uploadEstablishmentImage('logo', file)
    await uploadEstablishmentImage('capa', file)
    await uploadGalleryPhoto(file)

    expect(requests).toHaveLength(3)
    for (const request of requests) {
      expect(request.contentType).toBe('multipart/form-data')
      expect(request.data).toBeInstanceOf(FormData)
      expect((request.data as FormData).get('file')).toBe(file)
    }
  })
})
