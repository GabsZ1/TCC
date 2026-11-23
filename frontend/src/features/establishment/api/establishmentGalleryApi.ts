/** Envia, ordena e remove fotos da galeria do estabelecimento. */
import { api } from '@/lib/api'

export interface GalleryPhoto { id: number; position: number }
export const GALLERY_LIMIT = 15

export function fetchGallery(establishmentId: number, mine = false) {
  const path = mine ? '/estabelecimentos/me/perfil/galeria' : `/estabelecimentos/${establishmentId}/perfil/galeria`
  return api.get<GalleryPhoto[]>(path).then((response) => response.data)
}

export function fetchGalleryImage(establishmentId: number, photoId: number, mine = false) {
  const path = mine ? `/estabelecimentos/me/perfil/galeria/${photoId}` : `/estabelecimentos/${establishmentId}/perfil/galeria/${photoId}`
  return api.get<Blob>(path, { responseType: 'blob' }).then((response) => new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(response.data)
  }))
}

export function uploadGalleryPhoto(file: File) {
  const form = new FormData()
  form.append('file', file)
  return api.post<GalleryPhoto>('/estabelecimentos/me/perfil/galeria', form, { headers: { 'Content-Type': 'multipart/form-data' } })
    .then((response) => response.data)
}

export function deleteGalleryPhoto(photoId: number) {
  return api.delete(`/estabelecimentos/me/perfil/galeria/${photoId}`)
}

export function reorderGallery(photoIds: number[]) {
  return api.put<GalleryPhoto[]>('/estabelecimentos/me/perfil/galeria/ordem', { photoIds }).then((response) => response.data)
}
