/** Consulta de CEP para preencher o endereço automaticamente. */

export interface CepAddress {
  street: string
  neighborhood: string
  city: string
  state: string
}

export class CepNotFoundError extends Error {
  constructor() {
    super('CEP não encontrado.')
    this.name = 'CepNotFoundError'
  }
}

/** Mantém só os dígitos (no máximo 8). */
export function cepDigits(value: string | null | undefined) {
  return (value ?? '').replace(/\D/g, '').slice(0, 8)
}

/** Exibe o CEP como 00000-000 (o valor salvo continua só com dígitos). */
export function formatCep(value: string | null | undefined) {
  const digits = cepDigits(value)
  return digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits
}

interface ViaCepResponse {
  erro?: boolean | string
  logradouro?: string
  bairro?: string
  localidade?: string
  uf?: string
}

interface BrasilApiResponse {
  street?: string
  neighborhood?: string
  city?: string
  state?: string
}

export function parseViaCep(data: ViaCepResponse): CepAddress {
  if (data.erro) throw new CepNotFoundError()
  return { street: data.logradouro ?? '', neighborhood: data.bairro ?? '', city: data.localidade ?? '', state: data.uf ?? '' }
}

export function parseBrasilApi(data: BrasilApiResponse): CepAddress {
  return { street: data.street ?? '', neighborhood: data.neighborhood ?? '', city: data.city ?? '', state: data.state ?? '' }
}

/**
 * Busca o endereço na ViaCEP; se ela estiver fora do ar, tenta a BrasilAPI.
 * Lança CepNotFoundError quando o CEP não existe. Usa fetch (e não o cliente da API)
 * para não enviar o token do SalonHub a um serviço externo.
 */
export async function lookupCep(cep: string, signal?: AbortSignal): Promise<CepAddress> {
  const digits = cepDigits(cep)
  if (digits.length !== 8) throw new CepNotFoundError()
  try {
    const response = await fetch(`https://viacep.com.br/ws/${digits}/json/`, { signal })
    if (response.status === 400) throw new CepNotFoundError()
    if (!response.ok) throw new Error(`ViaCEP respondeu ${response.status}`)
    return parseViaCep(await response.json())
  } catch (error) {
    if (error instanceof CepNotFoundError || signal?.aborted) throw error
    const response = await fetch(`https://brasilapi.com.br/api/cep/v1/${digits}`, { signal })
    if (response.status === 404) throw new CepNotFoundError()
    if (!response.ok) throw new Error(`BrasilAPI respondeu ${response.status}`)
    return parseBrasilApi(await response.json())
  }
}
