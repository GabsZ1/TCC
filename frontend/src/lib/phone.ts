/** Telefones brasileiros (DDD + 8 ou 9 dígitos). O backend guarda só os dígitos. */

export function phoneDigits(value: string) {
  return value.replace(/\D/g, '')
}

export function isValidPhone(value: string) {
  return /^[1-9]\d{9,10}$/.test(phoneDigits(value))
}

export function formatPhone(value: string | null | undefined) {
  const digits = phoneDigits(value ?? '')
  if (digits.length === 11) return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`
  if (digits.length === 10) return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`
  return value ?? ''
}

/** Máscara dinâmica: fixo (8 dígitos) ou celular (9 dígitos). */
export const PHONE_MASK = [{ mask: '(00) 0000-0000' }, { mask: '(00) 00000-0000' }]
