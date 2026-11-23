/** Converte e valida períodos de bloqueio para exibição e envio à API. */
import { addBusinessDays } from '@/lib/dateTime'

/** A API recebe um intervalo semiaberto; o dia final escolhido na tela é inclusivo. */
export function blocksQueryRange(start: string, end: string) {
  return { startsAt: `${start}T00:00`, endsAt: `${addBusinessDays(end, 1)}T00:00` }
}
