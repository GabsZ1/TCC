/** Formatadores usados nas telas de relatório (pt-BR). */

export function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

export function formatPercentage(value: number) {
  return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value) + '%'
}

/** Data ISO "AAAA-MM-DD" → "DD/MM/AAAA", sem deslocamento de fuso. */
export function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`))
}

/** Data/hora sem fuso vinda da API (horário local do estabelecimento) → "DD/MM/AAAA HH:mm". */
export function formatDateTime(value: string) {
  const [date, time = ''] = value.split('T')
  return `${formatDate(date)} ${time.slice(0, 5)}`.trim()
}

/** Hoje no fuso do navegador, em "AAAA-MM-DD" (para o atributo max dos campos de data). */
export function todayIso() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}
