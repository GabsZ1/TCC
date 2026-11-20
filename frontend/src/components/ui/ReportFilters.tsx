/** Peças compartilhadas pelos filtros dos relatórios, para todas as telas terem o mesmo visual. */
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

/** Linha "Filtros ativos" exibida abaixo do card de filtros quando há algum filtro aplicado. */
export function ActiveFilters({ children }: { children: ReactNode }) {
  return (
    <div className="mt-4 flex flex-wrap items-center gap-2" aria-label="Filtros ativos">
      <span className="text-xs font-bold uppercase tracking-[0.12em] text-text-500">Filtros ativos:</span>
      {children}
    </div>
  )
}

/** Etiqueta de um filtro aplicado, com botão para remover só aquele filtro. */
export function FilterTag({ children, onRemove }: { children: ReactNode; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-primary-200 bg-primary-50 py-1 pl-3 pr-1.5 text-xs font-semibold text-primary-800">
      {children}
      <button type="button" onClick={onRemove} className="grid size-5 place-items-center rounded-full hover:bg-primary-100" aria-label="Remover filtro" title="Remover este filtro"><X size={12} /></button>
    </span>
  )
}
