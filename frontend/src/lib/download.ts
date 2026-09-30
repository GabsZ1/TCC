import { api } from '@/lib/api'

/** Baixa um arquivo de uma rota autenticada da API (o token vai no cabeçalho, por isso não dá para usar um link direto). */
export async function downloadFromApi(path: string, params: object, filename: string) {
  const response = await api.get<Blob>(path, { params, responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
