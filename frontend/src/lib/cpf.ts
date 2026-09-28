/** CPF: validação dos dígitos verificadores e formatação (000.000.000-00). */

export function cpfDigits(value: string) {
  return value.replace(/\D/g, '')
}

export function isValidCpf(value: string) {
  const digits = cpfDigits(value)
  if (digits.length !== 11 || /^(\d)\1{10}$/.test(digits)) return false
  const check = (length: number) => {
    let sum = 0
    for (let index = 0; index < length; index += 1) sum += Number(digits[index]) * (length + 1 - index)
    const remainder = (sum * 10) % 11
    return remainder === 10 ? 0 : remainder
  }
  return check(9) === Number(digits[9]) && check(10) === Number(digits[10])
}

export function formatCpf(value: string | null | undefined) {
  const digits = cpfDigits(value ?? '')
  if (digits.length !== 11) return value ?? ''
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`
}
