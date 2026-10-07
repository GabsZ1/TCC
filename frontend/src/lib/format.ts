/** Formatadores usados nas telas de relatório (pt-BR). */

import { businessToday, formatBusinessDate, formatBusinessDateTime } from './dateTime'

export function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

export function formatPercentage(value: number) {
  return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value) + '%'
}

/** Data ISO "AAAA-MM-DD" → "DD/MM/AAAA", sem deslocamento de fuso. */
export function formatDate(value: string) {
  return formatBusinessDate(value)
}

/** Data/hora sem fuso vinda da API (horário local do estabelecimento) → "DD/MM/AAAA HH:mm". */
export function formatDateTime(value: string) {
  return formatBusinessDateTime(value)
}

/** Hoje no fuso do navegador, em "AAAA-MM-DD" (para o atributo max dos campos de data). */
export function todayIso() {
  return businessToday()
}
