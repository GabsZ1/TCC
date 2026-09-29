/** Tolerância de atraso antes de permitir registrar a falta (igual ao backend). */
export const NO_SHOW_TOLERANCE_MINUTES = 15

/**
 * A falta pode ser registrada a partir do início + tolerância. `now` pode ser a hora local do
 * estabelecimento como texto ("AAAA-MM-DDTHH:mm:ss"), comparada no mesmo formato de startsAt.
 */
export function canMarkNoShow(startsAt: string, now: Date | string = new Date()) {
  const nowMs = typeof now === 'string' ? new Date(now).getTime() : now.getTime()
  return new Date(startsAt).getTime() + NO_SHOW_TOLERANCE_MINUTES * 60_000 <= nowMs
}
