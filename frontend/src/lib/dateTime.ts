/** Converte datas e horários respeitando as regras de apresentação do negócio. */
export const BUSINESS_TIME_ZONE = 'America/Sao_Paulo'

type DateStyle = Intl.DateTimeFormatOptions['dateStyle']

function partsInBusinessTimeZone(now: Date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(now)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? '00'
  return { year: part('year'), month: part('month'), day: part('day'), hour: part('hour'), minute: part('minute'), second: part('second') }
}

function dateOnlyAsUtc(value: string) {
  const [year, month, day] = value.slice(0, 10).split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day, 12))
}

export function businessToday(now = new Date()) {
  const { year, month, day } = partsInBusinessTimeZone(now)
  return `${year}-${month}-${day}`
}

export function businessNowLocalDateTime(now = new Date()) {
  const { year, month, day, hour, minute, second } = partsInBusinessTimeZone(now)
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`
}

export function addBusinessDays(value: string, days: number) {
  const date = dateOnlyAsUtc(value)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

export function addMinutesToBusinessLocalDateTime(value: string, minutes: number) {
  const [date, time = '00:00:00'] = value.split('T')
  const [year, month, day] = date.split('-').map(Number)
  const [hour = 0, minute = 0, second = 0] = time.split(':').map(Number)
  return new Date(Date.UTC(year, month - 1, day, hour, minute + minutes, second)).toISOString().slice(0, 19)
}

export function businessLocalDateTimeDifferenceMs(start: string, end: string) {
  return Date.parse(`${end.length === 16 ? `${end}:00` : end}Z`) - Date.parse(`${start.length === 16 ? `${start}:00` : start}Z`)
}

export function normalizeBusinessLocalDateTime(value: string) {
  return value.length === 16 ? `${value}:00` : value.slice(0, 19)
}

export function hasBusinessLocalDateTimeStarted(value: string, now = new Date()) {
  return normalizeBusinessLocalDateTime(value) <= businessNowLocalDateTime(now)
}

export function isBusinessLocalDateTimeFuture(value: string, now = new Date()) {
  return normalizeBusinessLocalDateTime(value) > businessNowLocalDateTime(now)
}

export function formatBusinessDate(value: string, dateStyle: DateStyle = 'short') {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle, timeZone: 'UTC' }).format(dateOnlyAsUtc(value))
}

export function formatBusinessDateWithWeekday(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit', month: 'long', timeZone: 'UTC' }).format(dateOnlyAsUtc(value))
}

export function formatBusinessWeekday(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { weekday: 'short', timeZone: 'UTC' }).format(dateOnlyAsUtc(value)).replace('.', '')
}

export function formatBusinessDayNumber(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', timeZone: 'UTC' }).format(dateOnlyAsUtc(value))
}

export function formatBusinessMonthShort(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { month: 'short', timeZone: 'UTC' }).format(dateOnlyAsUtc(value)).replace('.', '')
}

export function formatBusinessDateTime(value: string) {
  const [date, time = ''] = value.split('T')
  return `${formatBusinessDate(date)} ${time.slice(0, 5)}`.trim()
}

/** Converte a seleção civil do FullCalendar para o LocalDateTime enviado à API. */
export function calendarDateToBusinessLocalDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`
}
