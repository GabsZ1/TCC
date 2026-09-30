import { api } from '@/lib/api'

export type ImageKind = 'logo' | 'capa'
const path = (kind: ImageKind) => `/estabelecimentos/me/perfil/imagens/${kind}`

export const uploadEstablishmentImage = (kind: ImageKind, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api.post(path(kind), form)
}
export const deleteEstablishmentImage = (kind: ImageKind) => api.delete(path(kind))
export const fetchEstablishmentImage = (establishmentId: number, kind: ImageKind, mine = false) =>
  api.get<Blob>(mine ? path(kind) : `/estabelecimentos/${establishmentId}/perfil/imagens/${kind}`, { responseType: 'blob' })
    .then(response => new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as string)
      reader.onerror = () => reject(reader.error)
      reader.readAsDataURL(response.data)
    }))
